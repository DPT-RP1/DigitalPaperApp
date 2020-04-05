package net.sony.dpt.ui.cli;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public enum CommandOption {
    ADDR("addr", true, "The ip address of the Digital Paper"),
    SERIAL("serial", true, "The serial number of the Digital Paper we want to auto discover"),
    DRYRUN("dryrun", "For commands that can run in dry mode, simulate their action"),
    INTERACTIVE("interactive", "For commands that can run in interactive mode, stop the process to ask for user input"),
    FORCE("force", "For commands that can run in force mode, continue despite validation errors");

    private String optionLongName;
    private String description;
    private boolean hasArg;
    private Option option;

    CommandOption(String longName, boolean hasArg, String description) {
        this.optionLongName = longName;
        this.hasArg = hasArg;
        this.description = description;
        option = new Option(optionLongName, optionLongName, hasArg, description);
    }

    CommandOption(String longName, String description) {
        this(longName, false, description);
    }

    public static Options options() {
        Options options = new Options();
        for (CommandOption commandOption : values()) {
            options.addOption(commandOption.option);
        }
        return options;
    }

    public String getOptionLongName() {
        return optionLongName;
    }

    public void setOptionLongName(String optionLongName) {
        this.optionLongName = optionLongName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isHasArg() {
        return hasArg;
    }

    public void setHasArg(boolean hasArg) {
        this.hasArg = hasArg;
    }

    public Option getOption() {
        return option;
    }

    public void setOption(Option option) {
        this.option = option;
    }
}
