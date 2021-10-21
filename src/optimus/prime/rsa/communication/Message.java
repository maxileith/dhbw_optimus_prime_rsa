package optimus.prime.rsa.communication;

import java.io.Serializable;

public class Message implements Serializable {

    /*
    private final String possiblePrimes;
    private final int sliceSize;
    private final int startIndex;
    private boolean foundRsaKey;
    private int prime1;
    private int prime2; */

    private MessageType type;
    private Object payload;

    /*
    public Message(String possiblePrimes, int sliceSize, int startIndex) {
        super();
        this.possiblePrimes = possiblePrimes;
        this.sliceSize = sliceSize;
        this.startIndex = startIndex;
    }*/

    public Message(MessageType type) {
        this.type = type;
        this.payload = null;
    }

    public Message(MessageType type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public MessageType getType() {
        return this.type;
    }

    public Object getPayload() {
        return this.payload;
    }
}
