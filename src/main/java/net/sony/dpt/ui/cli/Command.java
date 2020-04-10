package net.sony.dpt.ui.cli;


import java.util.*;

public enum Command {
    REGISTER("register", Collections.emptyList(), Collections.emptyList(), "Starts the pairing process with the Digital Paper"),
    PING("ping", "Tests the connection with the Digital Paper"),
    SYNC("sync", Collections.singletonList(CommandOption.DRYRUN), Collections.singletonList("local-sync-folder"), "Synchronizes a local folder with the Digital paper"),
    LIST_DOCUMENTS("list-documents", "Lists all documents"),
    DOCUMENT_INFO("document-info", "Prints all documents and their attributes, raw"),
    UPLOAD("upload", Collections.emptyList(), Arrays.asList("local-file", "[remote-file]"), "Sends a local file to the Digital Paper"),
    DOWNLOAD("download"),
    MOVE(Arrays.asList("move", "move-document"), Arrays.asList("source", "target")),
    COPY(Arrays.asList("copy", "copy-document"), Arrays.asList("source", "target")),
    NEW_FOLDER("new-folder", Collections.singletonList("remote-folder")),
    DELETE_FOLDER("delete-folder", Collections.singletonList("remote-file")),
    DELETE("delete", Collections.singletonList("remote-file")),
    PRINT("print", Collections.emptyList(), Collections.singletonList("local-file"), "Sends a pdf to the Digital Paper, and opens it immediately"),
    WATCH_PRINT("watch-print", Collections.emptyList(), Collections.singletonList("local-folder"), "Watches a folder, and print pdfs on creation/modification in this folder"),
    SCREENSHOT("screenshot", Collections.singletonList("png-file")),
    WHITEBOARD("whiteboard", "Shows a landscape half-scale projection of the digital paper, refreshed every second"),
    WHITEBOARD_HTML("whiteboard-html", "Opens a distribution server with /frontend path feeding the images from the Digital Paper"),
    DIALOG("dialog", Collections.emptyList(), Arrays.asList("title", "content", "button"), "Prints a dialog on the Digital Paper"),
    GET_OWNER(Arrays.asList("get-owner", "show-owner")),
    SET_OWNER("set-owner"),
    WIFI_LIST("wifi-list"),
    WIFI_SCAN("wifi-scan"),
    WIFI_ADD("wifi-add"),
    WIFI_DEL("wifi-del"),
    WIFI("wifi"),
    WIFI_ENABLE("wifi-enable"),
    WIFI_DISABLE("wifi-disable"),
    BATTERY("battery", "Shows the battery status informations"),
    STORAGE("storage", "Shows the storage status informations"),
    CHECK_FIRMWARE("check-firmware", "Check if a new firmware version has been published"),
    UPDATE_FIRMWARE("update-firmware", Collections.singletonList(CommandOption.FORCE), Collections.emptyList(), "BETA - NON FUNCTIONAL"),
    RAW_GET("get", Collections.emptyList(), Collections.singletonList("url"), "Sends and display a GET request to the Digital Paper"),
    HELP(Arrays.asList("help", "command-help"), "Prints this message");

    private final List<String> commandNames;
    private static final Map<String, Command> commandMap = new HashMap<>();
    private final List<CommandOption> commandOptions;
    private final List<String> argumentNames;
    private String description;

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

    Command(String command, String description) {
        this(command);
        this.description = description;
    }

    Command(String command, List<CommandOption> availableOptions, List<String> arguments, String description) {
        this.commandNames = Collections.singletonList(command);
        this.commandOptions = availableOptions;
        this.argumentNames = arguments;
        this.description = description;
    }

    Command(List<String> commands, List<CommandOption> availableOptions, List<String> arguments, String description) {
        this.commandNames = commands;
        this.commandOptions = availableOptions;
        this.argumentNames = arguments;
        this.description = description;
    }

    Command(List<String> commandNames, String description) {
        this(commandNames, Collections.emptyList(), Collections.emptyList(), description);
    }

    public static Command parse(String[] args) {
        if (!commandMap.containsKey(args[0])) return HELP;
        return commandMap.get(args[0]);
    }

    public static String printHelp() {
        StringBuilder helpBuilder = new StringBuilder("dpt command [parameters] [-options] [-addr] [-serial]\n");
        for (Command command : values()) {
            helpBuilder.append("\t").append(command.commandNames.get(0)).append(" ");
            for (String param : command.argumentNames) {
                helpBuilder.append(param).append(" ");
            }
            for (CommandOption commandOption : command.commandOptions) {
                helpBuilder.append("[-").append(commandOption.getOptionLongName()).append("] ");
            }
            helpBuilder.append("\n");
            if (command.description != null && !command.description.isEmpty()) helpBuilder.append("\t\t" ).append(command.description).append("\n");
        }
        return helpBuilder.toString();
    }

    public static void main(String[] args) {
        System.out.println(Command.printHelp());
    }
}
