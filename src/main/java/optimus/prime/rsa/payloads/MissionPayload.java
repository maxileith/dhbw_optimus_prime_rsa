package optimus.prime.rsa.payloads;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;

public class MissionPayload implements Serializable {
    private final String cipher;
    private final List<BigInteger> primes;
    private final BigInteger pubKeyRsa;

    public MissionPayload(BigInteger pubKeyRsa, String cipher, List<BigInteger> primes) {
        this.cipher = cipher;
        this.primes = primes;
        this.pubKeyRsa = pubKeyRsa;
    }

    public String getCipher() {
        return this.cipher;
    }

    public List<BigInteger> getPrimes() {
        return this.primes;
    }

    public BigInteger getPubKeyRsa() {
        return this.pubKeyRsa;
    }
}
