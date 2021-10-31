package optimus.prime.rsa.server.communication.payloads;

import java.io.Serializable;
import java.util.Queue;

@SuppressWarnings("ClassCanBeRecord")
public class UnfinishedSlicesPayload implements Serializable {
    private final Queue<SlicePayload> unfinishedSlices;

    public UnfinishedSlicesPayload(Queue<SlicePayload> unfinishedSlices) {
        this.unfinishedSlices = unfinishedSlices;
    }

    public Queue<SlicePayload> getUnfinishedSlices() {
        return unfinishedSlices;
    }
}
