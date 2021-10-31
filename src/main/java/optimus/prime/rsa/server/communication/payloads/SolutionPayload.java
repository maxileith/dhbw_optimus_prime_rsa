package optimus.prime.rsa.server.communication.payloads;

import java.io.Serializable;
import java.math.BigInteger;

@SuppressWarnings("ClassCanBeRecord")
public class SolutionPayload implements Serializable {
    private final BigInteger prime1;
    private final BigInteger prime2;

    public static final SolutionPayload NO_SOLUTION = new SolutionPayload(BigInteger.ZERO, BigInteger.ZERO);

    public SolutionPayload(BigInteger prime1, BigInteger prime2) {
        this.prime1 = prime1;
        this.prime2 = prime2;
    }

    public BigInteger getPrime1() {
        return prime1;
    }

    public BigInteger getPrime2() {
        return prime2;
    }

    public String toString() {
        return "Prime1: " + this.prime1 + "; Prime2: " + this.prime2;
    }
}
