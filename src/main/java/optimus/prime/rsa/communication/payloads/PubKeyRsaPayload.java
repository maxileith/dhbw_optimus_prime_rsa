package optimus.prime.rsa.communication.payloads;

import java.io.Serializable;
import java.math.BigInteger;

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
