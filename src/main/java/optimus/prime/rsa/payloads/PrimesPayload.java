package optimus.prime.rsa.payloads;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;

/**
 * This payload can be used to distribute the prime numbers
 */
@SuppressWarnings("ClassCanBeRecord")
public class PrimesPayload implements Serializable {
    private final List<BigInteger> primes;

    /**
     * Create a new {@link PrimesPayload}
     *
     * @param primes the {@link List} of primes
     */
    public PrimesPayload(List<BigInteger> primes) {
        this.primes = primes;
    }

    /**
     * Get the {@link List} of primes
     *
     * @return the {@link List} of primes
     */
    public List<BigInteger> getPrimes() {
        return this.primes;
    }
}
