package optimus.prime.rsa.payloads;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;

/**
 * This payload can be used to submit a mission
 */
@SuppressWarnings("ClassCanBeRecord")
public class MissionPayload implements Serializable {
    private final String cipher;
    private final List<BigInteger> primes;
    private final BigInteger pubKeyRsa;

    /**
     * Specify the details of a new {@link MissionPayload}
     *
     * @param pubKeyRsa the public key, that has to be cracked
     * @param cipher the cipher that has to be decrypted
     * @param primes the list of primes to use for attempting the crack
     */
    public MissionPayload(BigInteger pubKeyRsa, String cipher, List<BigInteger> primes) {
        this.cipher = cipher;
        this.primes = primes;
        this.pubKeyRsa = pubKeyRsa;
    }

    /**
     * Get the cipher
     *
     * @return the cipher of the mission as a {@link String}
     */
    public String getCipher() {
        return this.cipher;
    }

    /**
     * Get the primes
     *
     * @return the primes of the mission as a {@link List}
     */
    public List<BigInteger> getPrimes() {
        return this.primes;
    }

    /**
     * Get the public key
     *
     * @return the public key of the mission as a {@link BigInteger}
     */
    public BigInteger getPubKeyRsa() {
        return this.pubKeyRsa;
    }
}
