package optimus.prime.rsa.payloads;

import java.io.Serializable;
import java.util.Queue;

/**
 * This payload can be used to distribute the progress
 */
@SuppressWarnings("ClassCanBeRecord")
public class ProgressPayload implements Serializable {
    private final Queue<SlicePayload> lostSlices;
    private final int currentSliceStart;

    /**
     * Create a new {@link ProgressPayload}
     *
     * @param lostSlices slices that are being lost while a slave disconnected, or something similar
     * @param currentSliceStart the index that the next slice starts with
     */
    public ProgressPayload(Queue<SlicePayload> lostSlices, int currentSliceStart) {
        this.lostSlices = lostSlices;
        this.currentSliceStart = currentSliceStart;
    }

    /**
     * Get the lost slices {@link Queue}
     *
     * @return the lost slices {@link Queue}
     */
    public Queue<SlicePayload> getLostSlices() {
        return lostSlices;
    }

    /**
     * Get the index that the next slice starts with
     *
     * @return index that the next slice starts with
     */
    public int getCurrentSliceStart() {
        return this.currentSliceStart;
    }
}
