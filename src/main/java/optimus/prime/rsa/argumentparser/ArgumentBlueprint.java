package optimus.prime.rsa.argumentparser;

public class ArgumentBlueprint {

    private final boolean required;
    private final String description;
    private final String key;
    private String defaultValue = null;
    private String value = null;

    public ArgumentBlueprint(String key, boolean required, String description, String defaultValue) {
        this(key, required, description);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public ArgumentBlueprint(String key, boolean required, String description) {
        this.key = key;
        this.required = required;
        this.description = description;
    }

    protected boolean isRequired() {
        return required;
    }

    protected String getValue() {
        return value;
    }

    protected void setValue(String value) {
        this.value = value;
    }

    protected String getKey() {
        return this.key;
    }

    public String toString() {
        return String.format("%-17s %-65s %-9s %-15s", this.key, this.description, this.required, this.defaultValue);
    }
}
