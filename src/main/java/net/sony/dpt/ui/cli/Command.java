package net.sony.dpt.ui.cli;

import java.util.*;

public enum Command {
    REGISTER("register", Collections.emptyList(), Collections.emptyList(), "Starts the pairing process with the Digital Paper"),
    PING("ping", "Tests the connection with the Digital Paper"),
    SYNC("sync", Collections.singletonList(CommandOption.DRYRUN), Collections.singletonList("[local-sync-folder]"), "Synchronizes a local folder with the Digital paper. If no folder is given, it will use the one passed previously"),
    LIST_DOCUMENTS("list-documents", "Lists all documents"),
    DOCUMENT_INFO("document-info", "Prints all documents and their attributes, raw"),
    UPLOAD("upload", Collections.emptyList(), Arrays.asList("local-file", "[remote-file]"), "Sends a local file to the Digital Paper"),
    DOWNLOAD("download", "Downloads a remote file locally"),
    MOVE(Arrays.asList("move", "move-document"), Collections.emptyList(), Arrays.asList("source", "target"), "Moves a document on the device"),
    COPY(Arrays.asList("copy", "copy-document"), Collections.emptyList(), Arrays.asList("source", "target"), "Copies a document on the device"),
    NEW_FOLDER("new-folder", Collections.emptyList(), Collections.singletonList("remote-folder"), "Creates a new folder on the device"),
    DELETE_FOLDER("delete-folder", Collections.emptyList(), Collections.singletonList("remote-file"), "Remove a folder on the device"),
    DELETE("delete", Collections.emptyList(), Collections.singletonList("remote-file"), "Deletes a file on the device"),
    PRINT("print", Collections.emptyList(), Collections.singletonList("local-file"), "Sends a pdf to the Digital Paper, and opens it immediately"),
    WATCH_PRINT("watch-print", Collections.emptyList(), Collections.singletonList("local-folder"), "Watches a folder, and print pdfs on creation/modification in this folder"),
    SCREENSHOT("screenshot", Collections.emptyList(), Collections.singletonList("png-file"), "Takes a PNG screenshot and stores it locally"),
    WHITEBOARD("whiteboard", "Shows a landscape half-scale projection of the digital paper, refreshed every second"),
    WHITEBOARD_HTML("whiteboard-html", "Opens a distribution server with /frontend path feeding the images from the Digital Paper"),
    DIALOG("dialog", Collections.emptyList(), Arrays.asList("title", "content", "button"), "Prints a dialog on the Digital Paper"),
    GET_OWNER(Arrays.asList("get-owner", "show-owner"), "Displays the owner's name"),
    SET_OWNER("set-owner", Collections.emptyList(), Collections.singletonList("owner-name"), "Sets the owner's name"),
    WIFI_LIST("wifi-list", "Lists all wifi configured on the device"),
    WIFI_SCAN("wifi-scan", "Scans all wifi hotspot available around the device"),
    WIFI_ADD("wifi-add", "Adds a wifi hotspot (obsolete since the latest firmware)"),
    WIFI_DEL("wifi-del", "Deletes a wifi hotspot (obsolete since the latest firmware)"),
    WIFI("wifi", "Displays the current wifi configured"),
    WIFI_ENABLE("wifi-enable", "Enables the wifi network device"),
    WIFI_DISABLE("wifi-disable", "Disables the wifi network device"),
    BATTERY("battery", "Shows the battery status informations"),
    STORAGE("storage", "Shows the storage status informations"),
    CHECK_FIRMWARE("check-firmware", "Check if a new firmware version has been published"),
    UPDATE_FIRMWARE("update-firmware", Arrays.asList(CommandOption.FORCE, CommandOption.DRYRUN), Collections.emptyList(), "Check for update and update the firmware if needed. Will ask for confirmation before triggering the update. Use -dryrun to test the process."),
    RAW_GET("get", Collections.emptyList(), Collections.singletonList("url"), "Sends and display a GET request to the Digital Paper"),
    MOUNT("mount", Collections.emptyList(), Collections.singletonList("[mount-point]"), "FUSE-mounts the DPT at the specified mount point. If not mount point is specified, it will attempt to use the one passed previously"),
    INSERT_NOTE_TEMPLATE("insert-note-template", Collections.emptyList(), Arrays.asList("name", "path"), "Inserts a new note template from the specified file, with the specified name"),
    GET_CONFIGURATION("get-configuration", Collections.emptyList(), Collections.singletonList("path"), "Saves the system configuration to a local file at <path>"),
    SET_CONFIGURATION("set-configuration", Collections.emptyList(), Collections.singletonList("path"), "Send the system configuration from a local file at <path>"),
    ROOT("root", Collections.singletonList(CommandOption.DRYRUN), Collections.emptyList(), "BETA - Roots the device"),
    DIAG_FETCH("diag fetch", Collections.emptyList(), Arrays.asList("remote-path", "local-path"), "BETA - Downloads a files from the diagnostic mode, after root. See doc/diagnosis_mod_map.md"),
    DIAG_EXIT("diag exit", Collections.emptyList(), Collections.emptyList(), "Exits the diagnostic mode (triggers a reboot)"),
    UNPACK("unpack", Collections.emptyList(), Arrays.asList("pkg", "target-directory"), "Unpacks an update pkg file into a data and animation archives"),
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

    Command(String commandName) {
        this(Collections.singletonList(commandName), Collections.emptyList(), Collections.emptyList());
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
        if (args.length == 0) return HELP;
        return commandMap.getOrDefault(args[0], args.length > 1 ? commandMap.getOrDefault(args[0] + " " + args[1], HELP) : HELP);
    }

    private static String left(String left) {
        return String.format("  %-40s", left);
    }

    public static String printVersion() {
        return "dpt 1.0";
    }

    public static String printHelp() {
        StringBuilder helpBuilder = new StringBuilder();
        helpBuilder.append("dpt is an utility to manage a Sony Digital Paper device\n\n");

        helpBuilder.append("Usage: dpt COMMAND [PARAMETERS] [OPTIONS]\n\n");

        helpBuilder.append("Options that can be passed to every commands, but aren't mandatory to find the device:\n");
        helpBuilder.append(left("-addr=IP_ADDR")).append("ip address of the DPT\n");
        helpBuilder.append(left("-serial=SERIAL")).append("serial code of the device\n");

        helpBuilder.append("\nAvailable commands:\n");

        for (Command command : values()) {
            StringBuilder commandHelpBuilder = new StringBuilder();
            commandHelpBuilder.append(command.commandNames.get(0)).append(" ");
            for (String param : command.argumentNames) {
                commandHelpBuilder.append(param).append(" ");
            }
            for (CommandOption commandOption : command.commandOptions) {
                commandHelpBuilder.append("[-").append(commandOption.getOptionLongName()).append("] ");
            }
            helpBuilder.append(left(commandHelpBuilder.toString()));
            if (command.description != null && !command.description.isEmpty()) {
                helpBuilder.append(command.description);
            } else {
                helpBuilder.append("BETA");
            }
            helpBuilder.append("\n");
        }
        return helpBuilder.toString();
    }

    public List<String> getCommandNames() {
        return commandNames;
    }

    public static Map<String, Command> getCommandMap() {
        return commandMap;
    }

    public List<CommandOption> getCommandOptions() {
        return commandOptions;
    }

    public List<String> getArgumentNames() {
        return argumentNames;
    }

    public String getDescription() {
        return description;
    }

    public static void main(String[] args) {
        System.out.println(Command.printHelp());
    }
}
