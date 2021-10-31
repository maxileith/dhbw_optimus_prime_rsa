package optimus.prime.rsa.server.communication.payloads;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;

@SuppressWarnings("ClassCanBeRecord")
public class PrimesPayload implements Serializable {
    private final List<BigInteger> primes;

    public PrimesPayload(List<BigInteger> primes) {
        this.primes = primes;
    }

    public List<BigInteger> getPrimes() {
        return this.primes;
    }
}
