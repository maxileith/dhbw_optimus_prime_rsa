package optimus.prime.rsa.payloads;

import java.io.Serializable;
import java.math.BigInteger;

/**
 * This payload can be used to distribute the public key
 */
@SuppressWarnings("ClassCanBeRecord")
public class PubKeyRsaPayload implements Serializable {
    private final BigInteger pubKeyRsa;

    public PubKeyRsaPayload(BigInteger pubKeyRsa) {
        this.pubKeyRsa = pubKeyRsa;
    }

    public BigInteger getPubKeyRsa() {
        return this.pubKeyRsa;
    }
}
