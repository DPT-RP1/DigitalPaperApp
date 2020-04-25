package net.sony.dpt.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sony.dpt.ui.cli.Command;
import net.sony.util.LogWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * This stores the last command parameters, which allows then to autofill the params for a next run
 * E.g.:
 *  1. dpt sync /path/to/sync/folder
 *  -> This gets remembered
 *  2. dpt sync
 *  -> This fetches the params and uses /path/to/sync/folder
 *
 *  In effect what we'd want here is to serialize a Map<Command, List<Parameters>>
 */
public class LastCommandRunStore extends AbstractStore implements Store<Map<String, List<String>>> {

    private static final Path LAST_COMMAND_STORE_FILE = Path.of("commands.last_run");
    private final ObjectMapper objectMapper;

    private final LogWriter logWriter;

    public LastCommandRunStore(final Path storageRoot, final LogWriter logWriter) {
        super(storageRoot);
        objectMapper = new ObjectMapper();
        this.logWriter = logWriter;
    }

    public void store(Command command, String... args) {
        Map<String, List<String>> persisted = retrieve();
        persisted.put(command.toString(), Arrays.asList(args));
        store(persisted);
    }
    
    @Override
    public void store(Map<String, List<String>> commandMap) {
        Path file = storagePath.resolve(LAST_COMMAND_STORE_FILE);
        try {
            Files.write(file, objectMapper.writeValueAsBytes(commandMap));
        } catch (IOException e) {
            logWriter.log("Impossible to save last command parameter, continuing.");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, List<String>> retrieve() {
        Path file = storagePath.resolve(LAST_COMMAND_STORE_FILE);
        try {
            byte[] content = Files.readAllBytes(file);
            return (Map<String, List<String>>) objectMapper.readValue(content, Map.class);
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    public List<String> retrieve(Command command) {
        Map<String, List<String>> persisted = retrieve();
        return persisted.getOrDefault(command.toString(), null);
    }

    /**
     * Utility function doing all the work to retrive just one argument for simple commands
     * @param command The command we'll retrieve args for
     * @param arguments The args list passed to the CLI
     * @return The one argument, either from this store, or from the command-line.
     */
    public String retrieveOneArgument(Command command, List<String> arguments) {
        String singleArgument;
        if (arguments.size() - 1 < command.getArgumentNames().size()) {
            singleArgument = retrieve(command).get(0);
        } else {
            singleArgument = arguments.get(1);
            store(command, singleArgument);
        }
        return singleArgument;
    }
}
