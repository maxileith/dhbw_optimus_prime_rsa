package optimus.prime.rsa.payloads;

import java.io.Serializable;

/**
 * This payload can be used to exchange ciphers
 */
@SuppressWarnings("ClassCanBeRecord")
public class CipherPayload implements Serializable {
    private final String cipher;

    /**
     * Create a new {@link CipherPayload}
     *
     * @param cipher the cipher as a {@link String}
     */
    public CipherPayload(String cipher) {
        this.cipher = cipher;
    }

    /**
     * Get the cipher
     *
     * @return the cipher as a {@link String}
     */
    public String getCipher() {
        return this.cipher;
    }
}
