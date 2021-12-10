package optimus.prime.rsa.payloads;

import java.io.Serializable;

/**
 * This is a slice that defines the beginning and the end of a part
 * in the list of primes
 */
@SuppressWarnings("ClassCanBeRecord")
public class SlicePayload implements Serializable {
    private final int start;
    private final int end;

    public SlicePayload(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public int getStart() {
        return this.start;
    }

    public int getEnd() {
        return this.end;
    }

    public String toString() {
        return "[" + this.start + ":" + this.end + "]";
    }
}
