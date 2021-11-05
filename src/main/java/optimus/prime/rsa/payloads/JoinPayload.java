package optimus.prime.rsa.payloads;

import java.io.Serializable;

@SuppressWarnings("ClassCanBeRecord")
public class JoinPayload implements Serializable {
    private final int workers;

    public JoinPayload(int workers) {
        this.workers = workers;
    }

    public int getWorkers() {
        return this.workers;
    }
}
