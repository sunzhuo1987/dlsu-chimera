/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ph.edu.dlsu.chimera.components;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import org.jnetpcap.protocol.tcpip.Tcp;
import ph.edu.dlsu.chimera.core.Diagnostic;
import ph.edu.dlsu.chimera.pdu.PduAtomic;
import ph.edu.dlsu.chimera.util.UtilsPacket;
import ph.edu.dlsu.chimera.core.TcpSocketPair;
import ph.edu.dlsu.chimera.core.Connection;
import ph.edu.dlsu.chimera.core.tools.IntermodulePipe;

/**
 * Tracks states and ensures TCP delivery.
 *
 * @author John Lawrence M. Penafiel <penafieljlm@gmail.com>
 */
public final class ComponentStateTracker extends ComponentActiveProcessor<PduAtomic, PduAtomic> {

    public final ConcurrentHashMap<TcpSocketPair, Connection> stateTable;

    public ComponentStateTracker(IntermodulePipe<PduAtomic> inQueue,
            IntermodulePipe<PduAtomic> outQueue,
            ConcurrentHashMap<TcpSocketPair, Connection> stateTable) {
        super(inQueue, outQueue);
        this.setPriority(Thread.NORM_PRIORITY);
        this.stateTable = stateTable;
    }

    @Override
    protected PduAtomic process(PduAtomic input) throws Exception {
        if (this.stateTable != null) {
            synchronized (this.stateTable) {
                try {
                    if (input.packet.hasHeader(new Tcp())) {
                        //tcp packets
                        TcpSocketPair socks = UtilsPacket.getSocketPair(input.packet);
                        Tcp tcp = input.packet.getHeader(new Tcp());
                        //create state
                        if (!this.stateTable.containsKey(socks)) {
                            if (tcp.flags_SYN() && !tcp.flags_ACK()) {
                                this.stateTable.put(socks, new Connection(socks, input.packet.getCaptureHeader().timestampInNanos(), input.direction));
                            }
                        }
                        if (this.stateTable.containsKey(socks)) {
                            Connection connection = this.stateTable.get(socks);
                            input.setConnection(connection);
                            //update state
                            connection.update(input);
                            //attempt to delete state
                            if (connection.isDone()) {
                                this.stateTable.remove(socks);
                            }
                        }
                    }
                } catch (Exception ex) {
                }
                //forward
                return input;
            }
        } else {
            throw new Exception("Error: [State Tracker] stateTable is null.");
        }
    }

    @Override
    public synchronized ArrayList<Diagnostic> getDiagnostics() {
        ArrayList<Diagnostic> diag = super.getDiagnostics();
        if (this.stateTable != null) {
            diag.add(new Diagnostic("states", "State Table Size", this.stateTable.size()));
        } else {
            diag.add(new Diagnostic("states", "State Table Size", "N/A"));
        }
        if (this.inQueue != null) {
            diag.add(new Diagnostic("inqueue", "Inbound Queued Packets", this.inQueue.size()));
        } else {
            diag.add(new Diagnostic("inqueue", "Inbound Queued Packets", "N/A"));
        }
        if (this.outQueue != null) {
            diag.add(new Diagnostic("outqueue", "Outbound Queued Packets", this.outQueue.size()));
        } else {
            diag.add(new Diagnostic("outqueue", "Outbound Queued Packets", "N/A"));
        }
        return diag;
    }
}
