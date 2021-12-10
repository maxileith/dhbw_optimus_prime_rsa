package optimus.prime.rsa.payloads;

import java.io.Serializable;

/**
 * This payload can be used to request a join and
 * send the number of workers to the master
 */
@SuppressWarnings("ClassCanBeRecord")
public class JoinPayload implements Serializable {
    private final int workers;

    /**
     * Create a new {@link JoinPayload}
     *
     * @param workers the number of workers that the {@link optimus.prime.rsa.server.communication.Slave}
     *                wants to join with
     */
    public JoinPayload(int workers) {
        this.workers = workers;
    }

    /**
     * Get the number of workers
     *
     * @return the number of workers
     */
    public int getWorkers() {
        return this.workers;
    }
}
