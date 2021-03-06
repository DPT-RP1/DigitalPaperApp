package net.sony.dpt.command.sync;

import net.sony.dpt.network.DigitalPaperEndpoint;
import net.sony.dpt.command.documents.DocumentEntry;
import net.sony.dpt.command.documents.DocumentListResponse;
import net.sony.dpt.command.documents.DocumentCommand;
import net.sony.dpt.persistence.SyncStore;
import net.sony.util.LogWriter;
import net.sony.util.ProgressBar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

public class SyncCommand {

    private static final Path remoteRoot = Path.of("Document");
    private final DigitalPaperEndpoint digitalPaperEndpoint;
    private final SyncStore syncStore;
    private final LogWriter logWriter;
    private final Path localRoot;
    private final Map<Path, DocumentEntry> localFileMap;
    private final Map<Path, DocumentEntry> remoteFileMap;
    private final PathMatcher pdfMatcher = FileSystems.getDefault().getPathMatcher("glob:**.pdf");
    private final DocumentCommand documentCommand;
    private final DocumentCommand listDocumentsCommand;

    private final List<Path> toFetch;
    private final List<Path> toSend;
    private final List<Path> toDeleteRemotely;
    private final List<Path> toDeleteLocally;

    private final ProgressBar progressBar;

    int documentsToSyncCount;
    int documentsToSyncSizeMB;
    private int handledSoFarMB = 0;

    public SyncCommand(final Path localRoot,
                       final DocumentCommand listDocumentsCommand,
                       final DocumentCommand documentCommand,
                       final DigitalPaperEndpoint digitalPaperEndpoint,
                       final LogWriter logWriter,
                       final SyncStore syncStore,
                       final ProgressBar progressBar) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
        this.logWriter = logWriter;
        this.syncStore = syncStore;
        localFileMap = new HashMap<>();
        remoteFileMap = new HashMap<>();

        this.localRoot = localRoot;
        this.listDocumentsCommand = listDocumentsCommand;

        this.documentCommand = documentCommand;

        toFetch = new ArrayList<>();
        toSend = new ArrayList<>();
        toDeleteRemotely = new ArrayList<>();
        toDeleteLocally = new ArrayList<>();

        this.progressBar = progressBar;
        documentsToSyncCount = 0;
        documentsToSyncSizeMB = 0;
    }

    public Map<Path, DocumentEntry> loadRemoteDocuments(DocumentListResponse documentListResponse) {
        remoteFileMap.clear();

        documentListResponse.getEntryList().forEach(documentEntry -> {
            Path relativePath = remoteRoot.relativize(Path.of(documentEntry.getEntryPath()));
            remoteFileMap.put(relativePath, documentEntry);
        });

        return remoteFileMap;
    }

    public Map<Path, DocumentEntry> loadLocalDocuments(Path localRoot, boolean dryrun) throws IOException {
        logWriter.log("Synchronizing with local folder " + localRoot);
        localFileMap.clear();

        if (!Files.exists(localRoot)) {
            logWriter.log("The local path " + localRoot + " does not exist, creating...") ;
            if (dryrun) return localFileMap;
            Files.createDirectories(localRoot);
        }

        Files.walk(localRoot, FileVisitOption.FOLLOW_LINKS).forEach(path -> {
            if (!pdfMatcher.matches(path)) return; // We only ever want to upload pdfs.
            Path relativePath = localRoot.relativize(path);
            DocumentEntry documentEntry = new DocumentEntry();
            try {
                documentEntry.setModifiedDate(Date.from(Files.getLastModifiedTime(path).toInstant()));
            } catch (IOException ignored) {
            }
            try {
                documentEntry.setFileSize(Files.size(path));
            } catch (IOException ignored) {
            }
            documentEntry.setEntryPath(relativePath.toString());
            localFileMap.put(relativePath, documentEntry);
        });
        return localFileMap;
    }

    /**
     * @param dryrun If true, only displays what would happen, but do not transfer/delete anything
     */
    public void sync(boolean dryrun) throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        if (progressBar != null) progressBar.start();

        loadRemoteDocuments(listDocumentsCommand.listDocuments());
        loadLocalDocuments(localRoot, dryrun);

        Date lastSyncDate = syncStore.retrieveLastSyncDate();
        if (dryrun) {
            logWriter.log("Synchronization dry-run starting...");
        }

        sync(lastSyncDate, dryrun);

        if (!dryrun) {
            syncStore.storeLastSyncDate(new Date());
        }

        long delay = System.currentTimeMillis() - start;

        if (documentsToSyncCount == 0) {
            logWriter.log("Nothing to synchronize: both sides are identical.");
        } else {
            logWriter.log("Synchronized " + documentsToSyncCount + " files in " + delay / 1000 + " seconds.");
        }
    }

    public void sync(Date lastSync, boolean dryrun) throws IOException, InterruptedException {
        // We are in initialization mode for the remote
        if (remoteFileMap.isEmpty() && !localFileMap.isEmpty()) {
            logWriter.log("Initial sync, sending all to remote...");
            toSend.addAll(localFileMap.keySet());
        } else if (!remoteFileMap.isEmpty() && localFileMap.isEmpty()) {
            logWriter.log("Initial sync, fetching all from remote...");
            toFetch.addAll(remoteFileMap.keySet());
        } else if (remoteFileMap.isEmpty()) {
            logWriter.log("There is nothing to synchronize: both your local folder and the device are empty.");
        } else {
            // We are in tree merge mode
            logWriter.log("Starting sync...");

            handleExistOnlyInRemote(localFileMap, remoteFileMap, lastSync);
            handleExistOnlyInLocal(localFileMap, remoteFileMap, lastSync);
            handleFileDifference(localFileMap, remoteFileMap);
        }
        calculateStatistics();

        runAllTasks(dryrun);
    }

    private void handleExistOnlyInLocal(Map<Path, DocumentEntry> localFileMap, Map<Path, DocumentEntry> remoteFileMap, Date lastSync) {

        Set<Path> onlyLocal = new HashSet<>(localFileMap.keySet());
        onlyLocal.removeAll(remoteFileMap.keySet());

        // Now for those only in local, two possibilities: they were created locally, or were deleted remotely
        // With the last sync date we know:
        //  if the last sync is more recent than the local file: the file existed remotely and got deleted
        //  if the last sync is older than the local file: the local file was added between the last sync and now
        //  if the last sync never happened: then we conservatively not delete
        for (Path path : onlyLocal) {
            if (lastSync == null) {
                toSend.add(path);
            } else {
                DocumentEntry local = localFileMap.get(path);
                if (lastSync.compareTo(local.getModifiedDate()) > 0) {
                    toDeleteLocally.add(path);
                } else {
                    toSend.add(path);
                }
            }
        }
    }


    private void handleExistOnlyInRemote(Map<Path, DocumentEntry> localFileMap, Map<Path, DocumentEntry> remoteFileMap, Date lastSync) {
        Set<Path> onlyRemote = new HashSet<>(remoteFileMap.keySet());
        onlyRemote.removeAll(localFileMap.keySet());

        // Now for those only in remote, two possibilities: they were send another way on the remote device, or were deleted locally
        // With the last sync date we know:
        //  if the last sync is more recent than the remote file: the file existed locally and got deleted
        //  if the last sync is older than the remote file: the remote file was added between the last sync and now
        //  if the last sync never happened: then we conservatively not delete
        for (Path path : onlyRemote) {
            if (lastSync == null) {
                toFetch.add(path);
            } else {
                DocumentEntry remote = remoteFileMap.get(path);
                if (lastSync.compareTo(remote.getModifiedDate()) > 0) {
                    toDeleteRemotely.add(path);
                } else {
                    toFetch.add(path);
                }
            }
        }
    }

    private void handleFileDifference(Map<Path, DocumentEntry> localFileMap,
                                      Map<Path, DocumentEntry> remoteFileMap) {

        Set<Path> insersect = new HashSet<>(localFileMap.keySet());
        insersect.retainAll(remoteFileMap.keySet());

        for (Path path : insersect) {
            DocumentEntry local = localFileMap.get(path);
            DocumentEntry remote = remoteFileMap.get(path);

            Date localLastModified = local.getModifiedDate();
            Date remoteLastModified = remote.getModifiedDate();

            long localSize = local.getFileSize();
            long remoteSize = remote.getFileSize();

            if (localSize == remoteSize) continue;

            // Since we can't merge, the most recent takes priority
            if (localLastModified.compareTo(remoteLastModified) > 0) {
                toSend.add(path);
            } else {
                toFetch.add(path);
            }
        }
    }

    private void calculateStatistics() {
        logWriter.log("We will send " + toSend.size() + " files to the Digital Paper");
        logWriter.log("We will receive " + toFetch.size() + " files from the Digital Paper");
        logWriter.log("We will delete " + toDeleteLocally.size() + " files locally");
        logWriter.log("We will delete " + toDeleteRemotely.size() + " files remotely");

        documentsToSyncCount = toFetch.size() + toSend.size() + toDeleteLocally.size() + toDeleteRemotely.size();
        toFetch.forEach(path -> documentsToSyncSizeMB += remoteFileMap.get(path).getFileSize() / 1024 / 1024);
        toSend.forEach(path -> documentsToSyncSizeMB += localFileMap.get(path).getFileSize() / 1024 / 1024);

    }

    private void notifyProgress(String currentTask, String group, int groupRemaining, int handledSoFar, int totalElementsToHandle, int newSizeBytes, int documentsToSyncSizeMB) {
        this.handledSoFarMB += newSizeBytes / 1024 / 1024;
        if (progressBar != null) {
            progressBar.progressed(handledSoFar, totalElementsToHandle);
            progressBar.progressedSize(this.handledSoFarMB, documentsToSyncSizeMB);
            progressBar.current(currentTask);
            progressBar.remaining(group, groupRemaining);
            progressBar.repaint();
        }
    }

    private void runAllTasks(boolean dryrun) throws IOException, InterruptedException {

        if (progressBar != null) {
            progressBar.progressed(0, documentsToSyncCount);
            progressBar.progressed(0, documentsToSyncSizeMB);
            progressBar.remaining("Fetching from DPT", toFetch.size());
            progressBar.remaining("Sending to DPT", toSend.size());
            progressBar.remaining("Deleting locally", toDeleteLocally.size());
            progressBar.remaining("Deleting on the DPT", toDeleteRemotely.size());
            progressBar.current(null);
            progressBar.repaint();
        }

        int progress = 0;
        int localProgress = 0;
        for (Path path : toFetch) {
            localProgress += 1;
            progress += 1;
            notifyProgress(
                    "Fetching " + path.getFileName(),
                    "Fetching from DPT",
                    toFetch.size() - localProgress,
                    progress,
                    documentsToSyncCount,
                    (int) remoteFileMap.get(path).getFileSize(),
                    documentsToSyncSizeMB
            );
            fetchRemoteFile(path, dryrun);
        }

        localProgress = 0;
        for (Path path : toSend) {
            localProgress += 1;
            progress += 1;
            notifyProgress(
                    "Sending " + path.getFileName(),
                    "Sending to DPT",
                    toSend.size() - localProgress,
                    progress,
                    documentsToSyncCount,
                    (int) localFileMap.get(path).getFileSize(),
                    documentsToSyncSizeMB
            );
            sendLocalFile(path, dryrun);
        }

        localProgress = 0;
        for (Path path : toDeleteLocally) {
            localProgress += 1;
            progress += 1;
            notifyProgress(
                    "Deleting locally " + path.getFileName(),
                    "Deleting locally",
                    toDeleteLocally.size() - localProgress,
                    progress,
                    documentsToSyncCount,
                    0,
                    documentsToSyncSizeMB
            );

            deleteLocalFile(path, dryrun);
        }

        localProgress = 0;
        for (Path path : toDeleteRemotely) {
            localProgress += 1;
            progress += 1;
            notifyProgress(
                    "Deleting on the DPT " + path.getFileName(),
                    "Deleting on the DPT",
                    toDeleteRemotely.size() - localProgress,
                    progress,
                    documentsToSyncCount,
                    0,
                    documentsToSyncSizeMB
            );
            deleteRemoteFile(path, dryrun);
        }
        if (progressBar != null) progressBar.stop();
    }

    private void sendLocalFile(Path path, boolean dryrun) throws IOException, InterruptedException {
        if (!dryrun) {
            documentCommand.upload(localRoot.resolve(path), remoteRoot.resolve(path));
        }
    }

    private void fetchRemoteFile(Path path, boolean dryrun) throws IOException, InterruptedException {
        if (!dryrun) {
            Path target = localRoot.resolve(path);
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = digitalPaperEndpoint.downloadByRemoteId(
                    remoteFileMap.get(path).getEntryId()
            )) {
                Files.copy(
                        inputStream,
                        localRoot.resolve(path),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        }
    }

    private void deleteRemoteFile(Path path, boolean dryrun) throws IOException, InterruptedException {
        if (!dryrun) {
            digitalPaperEndpoint.deleteByDocumentId(remoteFileMap.get(path).getEntryId());
        }
    }

    private void deleteLocalFile(Path path, boolean dryrun) throws IOException {
        if (!dryrun) {
            Files.delete(localRoot.resolve(path));
        }
    }

}
