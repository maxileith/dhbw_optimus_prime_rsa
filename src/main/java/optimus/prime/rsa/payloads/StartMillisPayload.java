package optimus.prime.rsa.payloads;

import java.io.Serializable;

@SuppressWarnings("ClassCanBeRecord")
public class StartMillisPayload implements Serializable {
    private final long startMillis;

    public StartMillisPayload(long startMillis) {
        this.startMillis = startMillis;
    }

    public long getStartMillis() {
        return this.startMillis;
    }
}
