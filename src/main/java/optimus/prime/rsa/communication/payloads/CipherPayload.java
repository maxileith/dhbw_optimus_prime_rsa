package optimus.prime.rsa.communication.payloads;

import java.io.Serializable;

public class CipherPayload implements Serializable {
    private final String cipher;

    public CipherPayload(String cipher) {
        this.cipher = cipher;
    }

    public String getCipher() {
        return this.cipher;
    }
}
