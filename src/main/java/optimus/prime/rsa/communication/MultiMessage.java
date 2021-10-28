package optimus.prime.rsa.communication;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

class MultiMessage implements Serializable {

    private final List<Message> messages;

    public final static MultiMessage NONE = new MultiMessage();

    public MultiMessage() {
        this.messages = new ArrayList<>();
    }

    public void addMessage(Message m) {
        this.messages.add(m);
    }

    public List<Message> getAllMessages() {
        return this.messages;
    }
}