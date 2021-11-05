package optimus.prime.rsa;

import java.io.Serializable;

public class Message implements Serializable {

    private final MessageType type;
    private final Object payload;

    public Message(MessageType type) {
        this.type = type;
        this.payload = null;
    }

    public Message(MessageType type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public MessageType getType() {
        return this.type;
    }

    public Object getPayload() {
        return this.payload;
    }
}
