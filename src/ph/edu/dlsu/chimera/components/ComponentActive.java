package ph.edu.dlsu.chimera.components;

import ph.edu.dlsu.chimera.core.Diagnostic;
import java.util.ArrayList;

/**
 * An instance of this class constitutes a Component which runs on its own
 * separate Thread.
 *
 * @author John Lawrence M. Penafiel <penafieljlm@gmail.com>
 */
public abstract class ComponentActive extends Thread implements Component {

    /**
     * String array of errors
     */
    public final ArrayList<Exception> errors;
    /**
     * Flags whether or not the ComponentActive object is running
     */
    protected boolean running;

    /**
     * Constructs a new ComponentActive object.
     */
    public ComponentActive() {
        this.errors = new ArrayList<Exception>();
        this.running = false;
    }

    /**
     * Kills the component's thread.
     */
    public synchronized void kill() {
        this.running = false;
    }

    @Override
    public synchronized ArrayList<Diagnostic> getDiagnostics() {
        ArrayList<Diagnostic> diag = new ArrayList<Diagnostic>();
        diag.add(new Diagnostic("running", "Is Running", this.running));
        diag.add(new Diagnostic("errors", "Current Error Count", this.errors.size()));
        return diag;
    }

    /**
     * @return An ArrayList of Exception objects caught while the
     * ComponentActive object was running.
     */
    public synchronized ArrayList<Exception> pollErrors() {
        ArrayList<Exception> errs = new ArrayList<Exception>(this.errors);
        this.errors.clear();
        return errs;
    }

    @Override
    public void run() {
        synchronized (this) {
            this.running = true;
        }
        try {
            this.componentRun();
        } catch (Exception ex) {
            this.errors.add(ex);
        }
        synchronized (this) {
            this.running = false;
        }
    }

    /**
     * The task to be performed by the ComponentActive object.
     *
     * @throws java.lang.Exception
     */
    protected abstract void componentRun() throws Exception;
}
