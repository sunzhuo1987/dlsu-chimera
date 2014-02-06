/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ph.edu.dlsu.chimera.components;

import com.cedarsoftware.util.io.JsonWriter;
import de.tbsol.iptablesjava.IpTables;
import de.tbsol.iptablesjava.rules.IpRule;
import java.net.InetAddress;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.productivity.java.syslog4j.Syslog;
import org.productivity.java.syslog4j.SyslogIF;
import ph.edu.dlsu.chimera.core.Statistics;
import ph.edu.dlsu.chimera.core.TrafficDirection;
import ph.edu.dlsu.chimera.core.criteria.Criteria;
import ph.edu.dlsu.chimera.core.criteria.CriteriaInstance;
import ph.edu.dlsu.chimera.core.logs.Log;
import ph.edu.dlsu.chimera.core.logs.LogAttackConnection;
import ph.edu.dlsu.chimera.core.logs.LogAttackCriteria;
import ph.edu.dlsu.chimera.core.model.ModelLive;
import ph.edu.dlsu.chimera.core.tools.IntermodulePipe;
import ph.edu.dlsu.chimera.core.PduAtomic;
import ph.edu.dlsu.chimera.util.UtilsArray;
import ph.edu.dlsu.chimera.util.UtilsTraining;
import weka.core.Instance;
import weka.core.Instances;

/**
 *
 * @author John Lawrence M. Penafiel <penafieljlm@gmail.com>
 */
public class ComponentDetector extends ComponentActiveProcessor<PduAtomic, PduAtomic> {

    public static final String CHIMERA_CHAIN = "CHIMERA";
    public static final String FORWARD_CHAIN = "FORWARD";
    public static final String DROP_JUMP = "DROP";
    public final ModelLive model;
    public final List<Object> rulesMap;
    public final SyslogIF syslogLogger;
    public final Instances connDataInstances;
    public final HashMap<Criteria, Instances> criteriaDataInstances;
    public final boolean active;
    public final ConcurrentLinkedQueue<Log> logs;
    private IpTables iptable;

    public ComponentDetector(IntermodulePipe<PduAtomic> inQueue,
            ModelLive model,
            List<Object> rulesMap,
            InetAddress syslogServer,
            int syslogPort,
            boolean active) {
        super(inQueue, null);
        this.model = model;
        this.rulesMap = rulesMap;
        if (syslogServer != null) {
            this.syslogLogger = Syslog.getInstance("udp");
            this.syslogLogger.getConfig().setHost(syslogServer.getHostAddress());
            this.syslogLogger.getConfig().setPort(syslogPort);
        } else {
            this.syslogLogger = null;
        }
        this.connDataInstances = new Instances("connection", this.model.connectionSubModel.attributes, 0);
        this.connDataInstances.setClassIndex(this.connDataInstances.numAttributes() - 1);
        this.criteriaDataInstances = new HashMap<Criteria, Instances>();
        for (Criteria crt : this.model.criteriaSubModels.keySet()) {
            this.criteriaDataInstances.put(crt, new Instances(crt.expression, this.model.criteriaSubModels.get(crt).attributes, 0));
            this.criteriaDataInstances.get(crt).setClassIndex(this.criteriaDataInstances.get(crt).numAttributes() - 1);
        }
        this.active = active;
        this.logs = new ConcurrentLinkedQueue<Log>();
    }

    @Override
    protected void preLoop() throws Exception {
        if (this.active) {
            this.iptable = new IpTables("filter");
            this.iptable.flushEntries(ComponentDetector.FORWARD_CHAIN);
            //create master chain
            if (!this.iptable.getAllChains().contains(ComponentDetector.CHIMERA_CHAIN)) {
                this.iptable.createChain(ComponentDetector.CHIMERA_CHAIN);
            } else {
                this.iptable.flushEntries(ComponentDetector.CHIMERA_CHAIN);
            }
            IpRule toChimeraChain = new IpRule();
            toChimeraChain.setJump(ComponentDetector.CHIMERA_CHAIN);
            this.iptable.appendEntry(ComponentDetector.FORWARD_CHAIN, toChimeraChain);
            boolean ok;
            do {
                ok = true;
                try {
                    System.out.println("96");
                    this.iptable.commit();
                } catch (Exception ex) {
                    ok = false;
                }
            } while (!ok);
        }
    }

    @Override
    protected PduAtomic process(PduAtomic input) throws Exception {
        if (input.direction == TrafficDirection.Ingress) {
            boolean commit = false;
            //integrity check
            if (this.active) {
                if (this.rulesMap != null) {
                    if (this.iptable.getAllRules(ComponentDetector.CHIMERA_CHAIN).size() != this.rulesMap.size()) {
                        throw new Exception("Error: [Detector] The CHIMERA iptables chain has been tampered with.");
                    }
                }
            }
            //connection evaluation
            if (!this.evaluateAgainstConnection(input)) {
                //attack
                this.logConnectionViolation(input);
                if (this.active) {
                    if (this.rulesMap != null) {
                        //add rules
                        if (input.getConnection() != null && !this.rulesMap.contains(input.getConnection().sockets)) {
                            IpRule rule = input.getConnection().sockets.createRule();
                            if (rule != null) {
                                rule.setJump(ComponentDetector.DROP_JUMP);
                                this.iptable.appendEntry(ComponentDetector.CHIMERA_CHAIN, rule);
                                commit = true;
                            }
                            this.rulesMap.add(input.getConnection().sockets);
                        }
                    }
                }
            } else {
                //normal
                if (this.active) {
                    if (this.rulesMap != null) {
                        //remove rules
                        if (input.getConnection() != null && this.rulesMap.contains(input.getConnection().sockets)) {
                            if (this.active) {
                                int idx = this.rulesMap.indexOf(input.getConnection().sockets);
                                this.iptable.deleteNumEntry(ComponentDetector.CHIMERA_CHAIN, idx);
                                System.out.println("Conn Normal " + idx);
                                commit = true;
                                this.rulesMap.remove(input.getConnection().sockets);
                            }
                        }
                    }
                }
            }
            //criteria evaluation
            HashMap<Criteria, Boolean> crtEval = this.evaluateAgainstCriterias(input);
            for (Criteria crt : crtEval.keySet()) {
                CriteriaInstance inst = crt.createInstance(input.packet);
                if (!crtEval.get(crt)) {
                    //attack
                    this.logCriteriaViolation(input, crt, input.getConnection());
                    if (this.active) {
                        if (this.rulesMap != null) {
                            //create rules
                            if (!this.rulesMap.contains(inst)) {
                                IpRule rule = inst.criteria.createRule(input.packet);
                                if (rule != null) {
                                    rule.setJump(ComponentDetector.DROP_JUMP);
                                    this.iptable.appendEntry(ComponentDetector.CHIMERA_CHAIN, rule);
                                    commit = true;
                                }
                                this.rulesMap.add(inst);
                            }
                        }
                    }
                } else {
                    //normal
                    if (this.active) {
                        if (this.rulesMap != null) {
                            //remove rules
                            if (this.rulesMap.contains(inst)) {
                                int idx = this.rulesMap.indexOf(inst);
                                this.iptable.deleteNumEntry(ComponentDetector.CHIMERA_CHAIN, idx);
                                System.out.println("Crit Normal " + idx);
                                commit = true;
                                this.rulesMap.remove(inst);
                            }
                        }
                    }
                }
            }
            if (this.active && commit) {
                int i = 0;
                boolean ok;
                do {
                    ok = true;
                    try {
                        System.out.println("190-" + i);
                        this.iptable.commit();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        i++;
                        ok = false;
                    }
                } while (!ok);
            }
        } else {
            throw new Exception("Error: [Detector] Encountered egress packet.");
        }
        return null;
    }

    @Override
    protected void postLoop() throws Exception {
        //clean up
        if (this.active) {
            if (this.iptable.getAllChains().contains(ComponentDetector.CHIMERA_CHAIN)) {
                this.iptable.flushEntries(ComponentDetector.CHIMERA_CHAIN);
                boolean ok;
                do {
                    ok = true;
                    try {
                        System.out.println("210");
                        this.iptable.commit();
                    } catch (Exception ex) {
                        ok = false;
                    }
                } while (!ok);
            }
        }
    }

    protected boolean evaluateAgainstConnection(PduAtomic pkt) {
        boolean allow = true;
        Object[] connInst = UtilsTraining.getConnectionInstance(pkt, this.connDataInstances);
        try {
            if (!UtilsTraining.instanceIsNull(connInst)) {
                Object[] coreInst = UtilsTraining.getCoreInstance(pkt, this.connDataInstances);
                Object[] inst = UtilsArray.concat(coreInst, connInst);
                Instance _inst = new Instance(inst.length + 1);
                _inst.setDataset(this.connDataInstances);
                for (int i = 0; i < inst.length - 1; i++) {
                    if (inst[i] instanceof Number) {
                        _inst.setValue(i, ((Number) inst[i]).doubleValue());
                    } else {
                        _inst.setValue(i, (String) inst[i]);
                    }
                }
                _inst.setMissing(_inst.numAttributes() - 1);
                double evalResult = (this.model.connectionSubModel.attackClass == 1.0) ? 0.0 : 1.0;
                try {
                    evalResult = this.model.connectionSubModel.tree.classifyInstance(_inst);
                } catch (Exception ex) {
                }
                allow = evalResult != this.model.connectionSubModel.attackClass;
            }
        } catch (Exception ex) {
        }
        return allow;
    }

    protected HashMap<Criteria, Boolean> evaluateAgainstCriterias(PduAtomic pkt) {
        HashMap<Criteria, Boolean> report = new HashMap<Criteria, Boolean>();
        for (Criteria crt : this.model.criteriaSubModels.keySet()) {
            boolean allow = true;
            Object[] coreInst = UtilsTraining.getCoreInstance(pkt, this.criteriaDataInstances.get(crt));
            Object[] crtInst = UtilsTraining.getCriteriaInstance(crt, pkt, this.criteriaDataInstances.get(crt));
            try {
                if (!UtilsTraining.instanceIsNull(crtInst)) {
                    Object[] inst = UtilsArray.concat(coreInst, crtInst);
                    Instance _inst = new Instance(inst.length + 1);
                    _inst.setDataset(this.criteriaDataInstances.get(crt));
                    for (int i = 0; i < inst.length; i++) {
                        if (inst[i] instanceof Number) {
                            _inst.setValue(i, ((Number) inst[i]).doubleValue());
                        } else {
                            _inst.setValue(i, (String) inst[i]);
                        }
                    }
                    _inst.setMissing(_inst.numAttributes() - 1);
                    double evalResult = (this.model.criteriaSubModels.get(crt).attackClass == 1.0) ? 0.0 : 1.0;
                    try {
                        StringBuilder attb = new StringBuilder();
                        StringBuilder valb = new StringBuilder();
                        for (int i = 0; i < _inst.numValues() - 1; i++) {
                            attb = attb.append("|").append(_inst.attribute(i).name()).append("|");
                            if (_inst.attribute(i).isNominal()) {
                                valb = valb.append("|").append(_inst.stringValue(i)).append("|");
                            } else {
                                valb = valb.append("|").append(_inst.value(i)).append("|");
                            }
                        }
                        evalResult = this.model.criteriaSubModels.get(crt).tree.classifyInstance(_inst);
                    } catch (Exception ex) {
                    }
                    allow = evalResult != this.model.criteriaSubModels.get(crt).attackClass;
                }
            } catch (Exception ex) {
            }
            report.put(crt, allow);
        }
        return report;
    }

    protected void logConnectionViolation(PduAtomic pkt) {
        System.out.println("Conn Attack");
        LogAttackConnection log = new LogAttackConnection(new Date(), pkt.getConnection());
        this.logs.add(log);
        if (this.syslogLogger != null) {
            this.syslogLogger.alert(JsonWriter.toJson(log));
        }
    }

    protected void logCriteriaViolation(PduAtomic pkt, Criteria criteria, Statistics statistics) {
        System.out.println("Crit Attack");
        LogAttackCriteria log = new LogAttackCriteria(new Date(), criteria, pkt.packet, statistics);
        this.logs.add(log);
        if (this.syslogLogger != null) {
            this.syslogLogger.alert(JsonWriter.toJson(log));
        }
    }
}
