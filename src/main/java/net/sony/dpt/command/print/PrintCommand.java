package net.sony.dpt.command.print;

import net.sony.dpt.DigitalPaperEndpoint;
import net.sony.dpt.command.dialog.DialogCommand;
import net.sony.dpt.command.documents.TransferDocumentCommand;
import net.sony.dpt.command.ping.PingCommand;
import net.sony.util.LogWriter;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Print a local document:
 * 1. Open a modal: "Printing..."
 * 2. Send the document
 * 3. Open it remotely
 * 4. Close the modal
 */
public class PrintCommand {

    private final DigitalPaperEndpoint digitalPaperEndpoint;
    private final DialogCommand dialogCommand;
    private final TransferDocumentCommand transferDocumentCommand;
    private final static Path PRINT_ROOT = Path.of("Document/Received/");
    private final LogWriter logWriter;
    private final PingCommand pingCommand;

    private final List<Path> printQueue;

    public PrintCommand(final DigitalPaperEndpoint digitalPaperEndpoint,
                        final DialogCommand dialogCommand,
                        final TransferDocumentCommand transferDocumentCommand,
                        final LogWriter logWriter,
                        final PingCommand pingCommand) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
        this.dialogCommand = dialogCommand;
        this.transferDocumentCommand = transferDocumentCommand;
        this.logWriter = logWriter;
        this.pingCommand = pingCommand;

        this.printQueue = new ArrayList<>();
    }

    public UUID openPrintModal(Path remotePath) throws IOException, InterruptedException {
        UUID modalId = UUID.randomUUID();
        dialogCommand.show(
                modalId,
                "Printing...",
                "Please wait while the document is being printed:\n" + remotePath,
                "Hide",
                true
        );
        return modalId;
    }

    public void closePrintModal(UUID modalId) throws IOException, InterruptedException {
        dialogCommand.hide(modalId);
    }

    public void openOnDevice(String documentId) throws IOException, InterruptedException {
        digitalPaperEndpoint.openDocument(documentId);
    }

    public void print(Path localPath, boolean quiet) throws IOException, InterruptedException {
        Path targetPath = PRINT_ROOT.resolve(localPath.getFileName());

        logWriter.log("Printing " + localPath + " to " + targetPath + " on the device");
        if (!quiet) {
            UUID modalId = openPrintModal(targetPath);
            try {
                String documentId = transferDocumentCommand.upload(localPath, targetPath);
                openOnDevice(documentId);
            } finally {
                closePrintModal(modalId);
            }
        } else {
            transferDocumentCommand.upload(localPath, targetPath);
        }

        logWriter.log("Print job finished");
    }

    @SuppressWarnings("unchecked")
    public void processFileCreate(Path parent, WatchKey watchKey) throws IOException, InterruptedException, URISyntaxException {
        if (watchKey == null) return;

        for (WatchEvent<?> event : watchKey.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == OVERFLOW) continue;

            WatchEvent<Path> ev = (WatchEvent<Path>) event;
            Path newFile = parent.resolve(ev.context());

            if (!"application/pdf".equals(Files.probeContentType(newFile))) {
                logWriter.log(String.format("New file '%s'" + " is not a pdf", newFile));
                continue;
            }
            // We ping the device: if on, we print, if off we add to the queue
            if (pingCommand.pingQuiet()) print(newFile, false);
            else printQueue.add(newFile);
        }
        watchKey.reset();
    }
    public void watch(Path localFolderToWatch) throws IOException {
        WatchService watcher = FileSystems.getDefault().newWatchService();
        localFolderToWatch.register(watcher, ENTRY_CREATE, ENTRY_MODIFY);

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            WatchKey key = watcher.poll();
            try {
                processPrintQueue();
                processFileCreate(localFolderToWatch, key);
            } catch (Exception e) {
                logWriter.log("An " + e.getClass() + " occurred while printing, resetting the watcher...");
                if (key != null) key.reset();
            }
        }, 0, 5000, TimeUnit.MILLISECONDS);
    }

    private void processPrintQueue() throws IOException, URISyntaxException, InterruptedException {
        if (!printQueue.isEmpty() && pingCommand.pingQuiet()) {
            for (Path path : printQueue) {
                print(path, true);
            }
        }
    }
}
