package ph.edu.dlsu.chimera.messages;

import java.util.HashMap;
import ph.edu.dlsu.chimera.components.Component;
import ph.edu.dlsu.chimera.core.Session;

/**
 * An instance of this class constitutes a Command which requests diagnostics
 * information from the deployed system.
 *
 * @author John Lawrence M. Penafiel <penafieljlm@gmail.com>
 */
public class CommandDiagnose implements Command {

    /**
     * The name of the component to diagnose
     */
    public final String component;

    /**
     * Constructs a new CommandDiagnose object.
     *
     * @param component The name of the component to diagnose
     */
    public CommandDiagnose(String component) {
        this.component = component;
    }

    @Override
    public Response handleCommand(Session session, HashMap<String, Component> assembly) throws Exception {
        if (assembly == null) {
            throw new Exception("A deployment phase is not being executed!");
        }
        if (assembly.get(this.component) == null) {
            throw new Exception("No such component '" + this.component + "'.");
        }
        if (assembly.get(this.component).getDiagnostics() == null) {
            throw new Exception("Unable to get diagnostics component '" + this.component + "'.");
        }
        return new ResponseDiagnose(this.component, assembly.get(this.component).getDiagnostics());
    }
}
