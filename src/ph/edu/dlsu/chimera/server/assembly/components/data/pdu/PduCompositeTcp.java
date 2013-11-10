/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ph.edu.dlsu.chimera.server.assembly.components.data.pdu;

import java.util.concurrent.ConcurrentLinkedQueue;
import ph.edu.dlsu.chimera.server.assembly.components.assembler.Assembler;
import ph.edu.dlsu.chimera.core.Connection;

/**
 *
 * @author John Lawrence M. Penafiel <penafieljlm@gmail.com>
 */
public abstract class PduCompositeTcp extends PduComposite {

    public PduCompositeTcp(Connection connection,
            Assembler assembler,
            boolean inbound,
            long timestampInNanos) {
        super(connection, assembler, inbound, timestampInNanos);
    }
}
