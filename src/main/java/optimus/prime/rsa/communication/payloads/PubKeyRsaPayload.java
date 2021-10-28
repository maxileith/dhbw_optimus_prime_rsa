package optimus.prime.rsa.communication.payloads;

import java.io.Serializable;

public class PubKeyRsaPayload implements Serializable {
    private final String pubKeyRsa;

    public PubKeyRsaPayload(String pubKeyRsa) {
        this.pubKeyRsa = pubKeyRsa;
    }

    public String getPubKeyRsa() {
        return this.pubKeyRsa;
    }
}
