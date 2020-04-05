package net.sony.dpt.ui.cli;

import org.apache.commons.cli.Options;

import java.util.*;

public enum Command {
    REGISTER("register"),
    LIST_DOCUMENTS("list-documents"),
    DOCUMENT_INFO("document-info"),
    WIFI_LIST("wifi-list"),
    UPLOAD("upload", Arrays.asList("local-file", "[remote-file]")),
    DOWNLOAD("download"),
    DELETE_FOLDER("delete-folder", Collections.singletonList("remote-file")),
    DELETE("delete", Collections.singletonList("remote-file")),
    NEW_FOLDER("new-folder", Collections.singletonList("remote-folder")),
    MOVE(Arrays.asList("move", "move-document"), Arrays.asList("source", "target")),
    COPY(Arrays.asList("copy", "copy-document"), Arrays.asList("source", "target")),
    WIFI_SCAN("wifi-scan"),
    SCREENSHOT("screenshot", Collections.singletonList("png-file")),
    WHITEBOARD("whiteboard"),
    SYNC("sync", Collections.singletonList(CommandOption.DRYRUN), Collections.singletonList("local-sync-folder")),
    DIALOG("dialog"),
    GET_OWNER(Arrays.asList("get-owner", "show-owner")),
    SET_OWNER("set-owner"),
    PING("ping"),
    WIFI_ADD("wifi-add"),
    WIFI_DEL("wifi-del"),
    WIFI("wifi"),
    WIFI_ENABLE("wifi-enable"),
    WIFI_DISABLE("wifi-disable"),
    UPDATE_FIRMWARE("update-firmware"),
    PRINT("print"),
    WATCH_PRINT("watch-print"),
    HELP("command-help");

    private List<String> commandNames;
    private static Map<String, Command> commandMap = new HashMap<>();
    private List<CommandOption> commandOptions;
    private List<String> argumentNames;

    static {
        for (Command command : values()) {
            for (String name : command.commandNames) {
                commandMap.put(name, command);
            }
        }
    }

    Command(List<String> commandNames, List<CommandOption> availableOptions, List<String> arguments) {
        this.commandNames = commandNames;
        commandOptions = availableOptions;
        this.argumentNames = arguments;
    }

    Command(String commandName, List<CommandOption> availableOptions, List<String> arguments) {
        this(Collections.singletonList(commandName), availableOptions, arguments);
    }

    Command(String commandName, List<String> arguments) {
        this(Collections.singletonList(commandName), Collections.emptyList(), arguments);
    }

    Command(String commandName) {
        this(Collections.singletonList(commandName), Collections.emptyList(), Collections.emptyList());
    }

    Command(List<String> commandNames) {
        this(commandNames, Collections.emptyList(), Collections.emptyList());
    }

    Command(List<String> commandNames, List<String> argumentNames) {
        this(commandNames, Collections.emptyList(), argumentNames);
    }

    public static Command find(String[] args) {
        if (!commandMap.containsKey(args[0])) return HELP;
        return commandMap.get(args[0]);
    }

    public static String printHelp() {
        StringBuilder helpBuilder = new StringBuilder("dpt command [parameters] [-options]\n");
        for (Command command : values()) {
            helpBuilder.append("\t").append(command.commandNames.get(0)).append(" ");
            for (String param : command.argumentNames) {
                helpBuilder.append(param).append(" ");
            }
            for (CommandOption commandOption : command.commandOptions) {
                helpBuilder.append("[-").append(commandOption.getOptionLongName()).append("] ");
            }
            helpBuilder.append("\n");
        }
        return helpBuilder.toString();
    }

    public static void main(String[] args) {
        System.out.println(Command.printHelp());
    }
}
