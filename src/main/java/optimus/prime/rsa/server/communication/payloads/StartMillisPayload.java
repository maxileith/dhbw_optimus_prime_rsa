package optimus.prime.rsa.server.communication.payloads;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;

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
