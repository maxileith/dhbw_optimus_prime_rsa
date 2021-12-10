package optimus.prime.rsa;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * MultiMessages allow sending multiple payloads without having to
 * send multiple "single" messages
 */
public class MultiMessage implements Serializable {

    private final List<Message> messages;

    public final static MultiMessage NONE = new MultiMessage();

    /**
     * Create a new {@link MultiMessage}
     */
    public MultiMessage() {
        this.messages = new ArrayList<>();
    }

    /**
     * Add a {@link Message} to this {@link MultiMessage}
     *
     * @param m {@link Message} to add to this {@link MultiMessage}
     */
    public void addMessage(Message m) {
        this.messages.add(m);
    }

    /**
     * Get all {@link Message} og
     *
     * @return a {@link List} of all {@link Message}s within this {@link MultiMessage}
     */
    public List<Message> getAllMessages() {
        return this.messages;
    }
}