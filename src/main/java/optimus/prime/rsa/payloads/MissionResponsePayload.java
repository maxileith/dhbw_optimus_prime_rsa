package optimus.prime.rsa.payloads;

import java.io.Serializable;

/**
 * This payload can be used to inform the client about a found solution
 */
@SuppressWarnings("ClassCanBeRecord")
public class MissionResponsePayload implements Serializable {
    private final SolutionPayload solution;
    private final String text;

    /**
     * Create a new {@link MissionResponsePayload}
     *
     * @param solution the {@link SolutionPayload}
     * @param text the decrypted cipher
     */
    public MissionResponsePayload(SolutionPayload solution, String text) {
        this.solution = solution;
        this.text = text;
    }

    /**
     * Get the {@link SolutionPayload}
     *
     * @return the {@link SolutionPayload}
     */
    public SolutionPayload getSolution() {
        return this.solution;
    }

    /**
     * Get the decrypted cipher
     *
     * @return the decrypted cipher
     */
    public String getText() {
        return this.text;
    }
}
