package optimus.prime.rsa.argumentparser;

import optimus.prime.rsa.server.Utils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static optimus.prime.rsa.argumentparser.ArgumentBlueprint.TABLE_FORMAT;

public class ArgumentParser {

    private final Map<String, ArgumentBlueprint> map = new HashMap<>();
    private final List<ArgumentBlueprint> list = new ArrayList<>();

    public void addArgument(ArgumentBlueprint argument) {
        this.list.add(argument);
        this.map.put(argument.getKey(), argument);
    }

    public void load(String[] args) {
        Pattern keyPattern = Pattern.compile("^--([^\s]+)$");

        for (String a : args) {
            if (a.equals("--help")) {
                this.help();
            }
        }

        for (int i = 0; i < args.length; i++) {

            Matcher keyMatch = keyPattern.matcher(args[i]);
            if (!keyMatch.matches()) {
                Utils.err("Expected key, got \"" + args[i] + "\"");
                this.help();
            }
            String key = keyMatch.group(1);

            i++;
            try {
                if (keyPattern.matcher(args[i]).matches()) {
                    Utils.err("Expected value for key \"" + key + "\"");
                    this.help();
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {
                Utils.err("Expected value for key \"" + key + "\"");
                this.help();
            }
            if (keyPattern.matcher(args[i]).matches()) {
                Utils.err("Expected value for key \"" + key + "\"");
                this.help();
            }
            String value = args[i];

            try {
                this.map.get(key).setValue(value);
            } catch (NullPointerException ignored) {
                Utils.err("Unexpected key \"" + key + "\"");
                this.help();
            }
        }

        this.checkIntegrity();
    }

    private void checkIntegrity() {
        for (ArgumentBlueprint a : this.list) {
            if (a.isRequired() && a.getValue() == null) {
                Utils.err("key \"" + a.getKey() + "\" is required.");
                this.help();
            }
        }
    }

    private void help() {
        System.out.printf(TABLE_FORMAT, "key", "description", "required", "default");
        for (ArgumentBlueprint a : this.list) {
            System.out.print(a);
        }
        System.exit(0);
    }

    public String get(String key) {
        return this.map.get(key).getValue();
    }

}
