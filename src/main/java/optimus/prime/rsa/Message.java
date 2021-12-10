package optimus.prime.rsa;

import java.io.Serializable;

/**
 * The message format that is used to communicate with different
 * instances running this program
 */
public class Message implements Serializable {

    private final MessageType type;
    // The payload of the message is an object to keep
    // the class flexible in terms of payload
    private final Object payload;

    /**
     * Create a message without a payload
     *
     * @param type {@link MessageType} of the message
     */
    public Message(MessageType type) {
        this.type = type;
        this.payload = null;
    }
    /**
     * Create a message with a payload
     *
     * @param type {@link MessageType} of the message
     * @param payload payload
     */
    public Message(MessageType type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    /**
     * Get the {@link MessageType} of the {@link Message}
     *
     * @return the {@link MessageType} of the {@link Message}
     */
    public MessageType getType() {
        return this.type;
    }

    /**
     * Get the payload of the {@link Message}
     *
     * @return the payload of the {@link Message}
     */
    public Object getPayload() {
        return this.payload;
    }
}
