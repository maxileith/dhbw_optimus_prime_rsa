package optimus.prime.rsa.communication.payloads;

import java.io.Serializable;

public class ResultPayload implements Serializable {
    private final int prime1;
    private final int prime2;

    public ResultPayload(int prime1, int prime2) {
        this.prime1 = prime1;
        this.prime2 = prime2;
    }

    public int getPrime1() {
        return this.prime1;
    }

    public int getPrime2() {
        return this.prime2;
    }
}
