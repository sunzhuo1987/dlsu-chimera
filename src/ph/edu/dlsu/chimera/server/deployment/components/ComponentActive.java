package ph.edu.dlsu.chimera.server.deployment.components;

import java.util.logging.Level;
import java.util.logging.Logger;
import ph.edu.dlsu.chimera.core.Diagnostic;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import ph.edu.dlsu.chimera.server.Assembly;

/**
 * An instance of this class constitutes a ComponentActive which runs on its own
 * separate Thread.
 *
 * @author John Lawrence M. Penafiel <penafieljlm@gmail.com>
 */
public abstract class ComponentActive extends Thread implements Component {

    /**
     * String array of errors.
     */
    public final ArrayList<Exception> errors;
    /**
     * The assembly which this component is a member of.
     */
    public final Assembly assembly;
    /**
     * Flags whether or not the ComponentActive object is running.
     */
    protected boolean running;

    /**
     * Constructs a new ComponentActive object.
     *
     * @param assembly - the assembly which this component is a member of.
     */
    public ComponentActive(Assembly assembly) {
        this.errors = new ArrayList<>();
        this.assembly = assembly;
        this.running = false;
    }

    /**
     * Kills the component's thread.
     */
    public synchronized void kill() {
        this.running = false;
    }

    /**
     * @return a report on the current state of the ComponentActive object.
     */
    @Override
    public synchronized ArrayList<Diagnostic> getDiagnostics() {
        ArrayList<Diagnostic> diag = new ArrayList<>();
        diag.add(new Diagnostic("running", "Is Running", this.running));
        diag.add(new Diagnostic("errors", "Current Error Count", this.errors.size()));
        return diag;
    }

    public synchronized ArrayList<Exception> pollErrors() {
        ArrayList<Exception> errs = new ArrayList<>(this.errors);
        this.errors.clear();
        return errs;
    }

    /**
     * Set running flags when component is started.
     */
    @Override
    public void run() {
        synchronized (this) {
            this.running = true;
        }
        try {
            this.componentRun();
        } catch (Exception ex) {
            this.errors.add(ex);
            Logger.getLogger(ComponentActive.class.getName()).log(Level.WARNING, null, ex);
        }
        synchronized (this) {
            this.running = false;
        }
    }

    /**
     * The task of the component.
     */
    protected abstract void componentRun() throws Exception;
}
