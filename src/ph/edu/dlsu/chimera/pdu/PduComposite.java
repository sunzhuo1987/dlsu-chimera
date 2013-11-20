/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ph.edu.dlsu.chimera.pdu;

import java.util.concurrent.ConcurrentLinkedQueue;
import ph.edu.dlsu.chimera.assembler.Assembler;
import ph.edu.dlsu.chimera.core.Connection;

/**
 *
 * @author John Lawrence M. Penafiel <penafieljlm@gmail.com>
 */
public abstract class PduComposite extends Pdu {

    public final Connection connection;
    public final Assembler assembler;

    public PduComposite(Connection connection,
            Assembler assembler,
            boolean ingress,
            long timestampInNanos) {
        super(ingress, timestampInNanos);
        this.connection = connection;
        this.assembler = assembler;
    }
}
