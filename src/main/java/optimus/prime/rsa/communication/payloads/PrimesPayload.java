package optimus.prime.rsa.communication.payloads;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PrimesPayload implements Serializable {
    private List<Integer> primes;

    public PrimesPayload() {
        this.primes = new ArrayList<Integer>();
    }

    public PrimesPayload(List<Integer> primes) {
        this.primes = primes;
    }

    public void setPrimes(List<Integer> primes) {
        this.primes = primes;
    }

    public List<Integer> getPrimes() {
        return this.primes;
    }
}
