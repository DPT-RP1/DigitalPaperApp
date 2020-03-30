package net.sony.dpt.command.sync;

import net.sony.dpt.DigitalPaperEndpoint;
import net.sony.dpt.command.documents.DocumentEntry;
import net.sony.dpt.command.documents.DocumentListResponse;
import net.sony.dpt.persistence.SyncStore;
import net.sony.util.LogWriter;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class SyncCommand {

    private static final Path remoteRoot = Path.of("Document");
    private final DigitalPaperEndpoint digitalPaperEndpoint;
    private final SyncStore syncStore;
    private final LogWriter logWriter;
    private final Path localPath;
    private final DocumentListResponse remoteDocumentList;
    private Map<Path, DocumentEntry> localFileMap;
    private Map<Path, DocumentEntry> remoteFileMap;
    private PathMatcher pdfMatcher = FileSystems.getDefault().getPathMatcher("glob:**.pdf");
    private boolean dryrun = true;

    public SyncCommand(Path localPath, DocumentListResponse remoteDocumentList, DigitalPaperEndpoint digitalPaperEndpoint, LogWriter logWriter, SyncStore syncStore) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
        this.logWriter = logWriter;
        this.syncStore = syncStore;
        localFileMap = new HashMap<>();
        remoteFileMap = new HashMap<>();

        this.localPath = localPath;
        this.remoteDocumentList = remoteDocumentList;
    }

    public Map<Path, DocumentEntry> loadRemoteDocuments(DocumentListResponse documentListResponse) {
        remoteFileMap.clear();

        documentListResponse.getEntryList().forEach(documentEntry -> {
            Path relativePath = remoteRoot.relativize(Path.of(documentEntry.getEntryPath()));
            remoteFileMap.put(relativePath, documentEntry);
        });

        return remoteFileMap;
    }

    public Map<Path, DocumentEntry> loadLocalDocuments(Path localRoot) throws IOException {
        localFileMap.clear();

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
     * @throws IOException
     */
    public void sync(boolean dryrun) throws IOException {
        loadRemoteDocuments(remoteDocumentList);
        loadLocalDocuments(localPath);
        Date lastSyncDate = syncStore.retrieveLastSyncDate();
        if (dryrun) {
            logWriter.log("Synchronization dry-run starting...");
        }
        this.dryrun = dryrun;
        sync(lastSyncDate);
    }

    public void sync(Date lastSync) {

        // We are in initialization mode for the remote
        if (remoteFileMap.isEmpty() && !localFileMap.isEmpty()) {
            logWriter.log("Initial sync, sending all to remote...");
            localFileMap.keySet().forEach(this::sendLocalFile);
        } else if (!remoteFileMap.isEmpty() && localFileMap.isEmpty()) {
            logWriter.log("Initial sync, fetching all from remote...");
            remoteFileMap.keySet().forEach(this::fetchRemoteFile);
        } else if (!remoteFileMap.isEmpty()) {
            // We are in tree merge mode
            logWriter.log("Starting sync...");
            handleFileDifference(localFileMap, remoteFileMap);
            handleExistOnlyInRemote(localFileMap, remoteFileMap, lastSync);
            handleExistOnlyInLocal(localFileMap, remoteFileMap, lastSync);
        } else {
            logWriter.log("There is nothing to sync: both your local folder and the device are empty.");
        }
    }

    private void handleExistOnlyInLocal(Map<Path, DocumentEntry> localFileMap, Map<Path, DocumentEntry> remoteFileMap, Date lastSync) {
        Set<Path> onlyLocal = new HashSet<>(localFileMap.keySet());
        onlyLocal.removeAll(remoteFileMap.keySet());

        // Now for those only in local, two possibilities: they were created locally, or were deleted remotely
        // With the last sync date we know:
        //  if the last sync is more recent than the local file: the file existed remotely and got deleted
        //  if the last sync is older than the local file: the local file was added between the last sync and now
        //  if the last sync never happened: then we conservatively not delete
        onlyLocal.forEach(path -> {
            if (lastSync == null) {
                sendLocalFile(path);
            } else {
                DocumentEntry local = localFileMap.get(path);
                if (lastSync.compareTo(local.getModifiedDate()) > 0) {
                    deleteLocalFile(path);
                } else {
                    sendLocalFile(path);
                }
            }
        });
    }


    private void handleExistOnlyInRemote(Map<Path, DocumentEntry> localFileMap, Map<Path, DocumentEntry> remoteFileMap, Date lastSync) {
        Set<Path> onlyRemote = new HashSet<>(remoteFileMap.keySet());
        onlyRemote.removeAll(localFileMap.keySet());

        // Now for those only in remote, two possibilities: they were send another way on the remote device, or were deleted locally
        // With the last sync date we know:
        //  if the last sync is more recent than the remote file: the file existed locally and got deleted
        //  if the last sync is older than the remote file: the remote file was added between the last sync and now
        //  if the last sync never happened: then we conservatively not delete
        onlyRemote.forEach(path -> {
            if (lastSync == null) {
                fetchRemoteFile(path);
            } else {
                DocumentEntry remote = remoteFileMap.get(path);
                if (lastSync.compareTo(remote.getModifiedDate()) > 0) {
                    deleteRemoteFile(path);
                } else {
                    fetchRemoteFile(path);
                }
            }
        });
    }

    private void handleFileDifference(Map<Path, DocumentEntry> localFileMap,
                                      Map<Path, DocumentEntry> remoteFileMap) {
        Set<Path> insersect = new HashSet<>(localFileMap.keySet());
        insersect.retainAll(remoteFileMap.keySet());

        insersect.forEach(path -> {
            DocumentEntry local = localFileMap.get(path);
            DocumentEntry remote = remoteFileMap.get(path);

            Date localLastModified = local.getModifiedDate();
            Date remoteLastModified = remote.getModifiedDate();

            long localSize = local.getFileSize();
            long remoteSize = remote.getFileSize();

            if (localSize == remoteSize) return; // Do nothing, files are the same.

            // Since we can't merge, the most recent takes priority
            if (localLastModified.compareTo(remoteLastModified) > 0) {
                sendLocalFile(path);
            } else {
                fetchRemoteFile(path);
            }
        });
    }

    private void sendLocalFile(Path path) {
        logWriter.log("Sending " + path);
        if (!dryrun) {

        }
    }

    private void fetchRemoteFile(Path path) {
        logWriter.log("Fetching " + path);
        if (!dryrun) {

        }
    }

    private void deleteRemoteFile(Path path) {
        logWriter.log("Deleting remotely " + path);
        if (!dryrun) {

        }
    }

    private void deleteLocalFile(Path path) {
        logWriter.log("Deleting locally " + path);
        if (!dryrun) {

        }
    }

}
