package optimus.prime.rsa.communication.payloads;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class PrimesPayload implements Serializable {
    private final List<BigInteger> primes;

    public PrimesPayload() {
        this.primes = new ArrayList<BigInteger>();
    }

    public PrimesPayload(List<BigInteger> primes) {
        this.primes = primes;
    }

    public List<BigInteger> getPrimes() {
        return this.primes;
    }
}
