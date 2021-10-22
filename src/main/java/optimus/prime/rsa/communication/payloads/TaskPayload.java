package optimus.prime.rsa.communication.payloads;

import java.io.Serializable;

public class TaskPayload implements Serializable {
    private final int startIndex;
    private final int sliceSize;

    public TaskPayload(int startIndex, int sliceSize) {
        this.startIndex = startIndex;
        this.sliceSize = sliceSize;
    }

    public int getStartIndex() {
        return this.startIndex;
    }

    public int getSliceSize() {
        return this.sliceSize;
    }

    public String toString() {
        return "Start-Index: " + this.startIndex + ", Slice-Size: " + this.sliceSize;
    }
}
