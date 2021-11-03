package optimus.prime.rsa.server.communication.payloads;

import java.io.Serializable;
import java.util.Queue;

@SuppressWarnings("ClassCanBeRecord")
public class ProgressPayload implements Serializable {
    private final Queue<SlicePayload> lostSlices;
    private final int currentSliceStart;

    public ProgressPayload(Queue<SlicePayload> lostSlices, int currentSliceStart) {
        this.lostSlices = lostSlices;
        this.currentSliceStart = currentSliceStart;
    }

    public Queue<SlicePayload> getLostSlices() {
        return lostSlices;
    }

    public int getCurrentSliceStart() {
        return this.currentSliceStart;
    }
}
