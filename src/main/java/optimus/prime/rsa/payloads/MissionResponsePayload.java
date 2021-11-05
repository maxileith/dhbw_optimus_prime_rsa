package optimus.prime.rsa.payloads;

import java.io.Serializable;

@SuppressWarnings("ClassCanBeRecord")
public class MissionResponsePayload implements Serializable {
    private final SolutionPayload solution;
    private final String text;

    public MissionResponsePayload(SolutionPayload solution, String text) {
        this.solution = solution;
        this.text = text;
    }

    public SolutionPayload getSolution() {
        return this.solution;
    }

    public String getText() {
        return this.text;
    }
}
