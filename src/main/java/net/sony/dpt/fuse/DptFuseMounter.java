package net.sony.dpt.fuse;

import jnr.ffi.Pointer;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import net.sony.dpt.command.documents.DocumentEntry;
import net.sony.dpt.command.documents.DocumentListResponse;
import net.sony.dpt.command.documents.DocumentCommand;
import net.sony.dpt.command.documents.EntryType;
import net.sony.util.LogWriter;
import org.apache.commons.io.IOUtils;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Timespec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class DptFuseMounter extends FuseStubFS {

    private static final Path REMOTE_ROOT = Path.of("Document/");
    private static final Path LOCAL_ROOT = Path.of("/");

    private final DocumentCommand documentCommand;
    private final LogWriter logWriter;
    // The DPT cannot handle too many request in parallel. We need to lock and wait
    // for requests in this class.
    private static final Object dptLock = new Object();

    // This cache will help to read partial files TODO: definitely a good LRU use case !!
    private final ConcurrentMap<String, byte[]> fileCache;
    private final ConcurrentMap<String, byte[]> writeCache;
    private final ConcurrentMap<Path, DocumentEntry> documentEntriesMap;

    private static Path toRemote(Path localPath) {
        return REMOTE_ROOT.resolve(LOCAL_ROOT.relativize(localPath));
    }

    private static Path toLocal(Path remotePath) {
        return LOCAL_ROOT.resolve(REMOTE_ROOT.relativize(remotePath));
    }

    public DptFuseMounter(final DocumentCommand documentCommand, final LogWriter logWriter) {
        this.documentCommand = documentCommand;
        this.logWriter = logWriter;

        fileCache = new ConcurrentHashMap<>();
        writeCache = new ConcurrentHashMap<>();
        documentEntriesMap = new ConcurrentHashMap<>();
    }

    private int getFolderAttr(FileStat stat) {
        stat.st_mode.set(FileStat.S_IFDIR | 0_755);
        stat.st_nlink.set(2);
        return 0;
    }

    private int getFileAttr(DocumentEntry found, FileStat stat) {
        stat.st_mode.set(FileStat.S_IFREG | 0_444);
        stat.st_nlink.set(1);
        stat.st_size.set(found.getFileSize());

        Date createdDate = found.getCreateDate();
        Date modifiedDate = found.getModifiedDate();
        Date accessDate = found.getReadingDate();

        stat.st_ctim.tv_sec.set(createdDate.getTime() / 1000);
        stat.st_mtim.tv_sec.set(modifiedDate.getTime() / 1000);
        stat.st_atim.tv_sec.set(accessDate.getTime() / 1000);
        stat.st_birthtime.tv_sec.set(createdDate.getTime() / 1000);

        stat.st_ctim.tv_nsec.set(createdDate.getTime() * 1000);
        stat.st_mtim.tv_nsec.set(modifiedDate.getTime() * 1000);
        stat.st_atim.tv_nsec.set(accessDate.getTime() * 1000);
        stat.st_birthtime.tv_nsec.set(createdDate.getTime() * 1000);
        return 0;
    }

    @Override
    public int getattr(String path, FileStat stat) {
        Path searchPath = Path.of(path);
        if (!documentEntriesMap.containsKey(searchPath)) {
            // We could have either a directory or a completely wrong path
            if (LOCAL_ROOT.equals(searchPath)) {
                return getFolderAttr(stat);
            }
            return -ErrorCodes.ENOENT();
        }

        DocumentEntry found = documentEntriesMap.get(searchPath);
        switch (found.getEntryType()) {
            case FOLDER:
                return getFolderAttr(stat);
            case DOCUMENT:
                return getFileAttr(found, stat);
        }
        return 0;
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public int mkdir(String path, long mode) {
        Path localPath = Path.of(path);

        if (documentEntriesMap.containsKey(localPath)) return -ErrorCodes.EEXIST();
        try {
            synchronized (dptLock) { documentCommand.createFolderRecursively(toRemote(localPath)); }
        } catch (IOException | InterruptedException e) {
            return -ErrorCodes.EREMOTEIO();
        }

        documentEntriesMap.put(localPath, new DocumentEntry(EntryType.FOLDER));
        return 0;
    }

    @Override
    public int rmdir(String path) {
        Path localPath = Path.of(path);
        if (!documentEntriesMap.containsKey(localPath)) return -ErrorCodes.ENOENT();
        try {
            synchronized (dptLock) { documentCommand.deleteFolder(toRemote(localPath)); }
        } catch (IOException | InterruptedException e) { return -ErrorCodes.EREMOTEIO(); }

        Set<Path> toRemove = documentEntriesMap.keySet()
                .stream()
                .filter(candidate -> candidate.startsWith(localPath))
                .collect(Collectors.toSet());
        toRemove.forEach(documentEntriesMap::remove);
        return 0;
    }



    @Override
    public int rename(String oldpath, String newpath) {
        Path old = Path.of(oldpath);
        Path newP = Path.of(newpath);

        if (!documentEntriesMap.containsKey(old)) return -ErrorCodes.ENOENT();

        try {
            synchronized (dptLock) { documentCommand.move(toRemote(old), toRemote(newP)); }
        } catch (IOException | InterruptedException e) {
            return -ErrorCodes.EREMOTEIO();
        }

        DocumentEntry documentEntry = documentEntriesMap.remove(old);
        documentEntry.setEntryPath(newpath);
        documentEntriesMap.put(newP, documentEntry);
        return 0;
    }

    @Override
    public int unlink(String path) {
        Path localPath = Path.of(path);
        if (documentEntriesMap.containsKey(localPath)) return -ErrorCodes.ENOENT();

        try {
            synchronized (dptLock) { documentCommand.delete(toRemote(localPath)); }
        } catch (IOException | InterruptedException e) { return -ErrorCodes.EREMOTEIO(); }
        documentEntriesMap.remove(localPath);
        fileCache.remove(path);
        return 0;
    }

    @Override
    public int flush(String path, FuseFileInfo fi) {
        return 0;
    }

    @Override
    public int release(String path, FuseFileInfo fi) {
        if (writeCache.containsKey(path)) {
            if (writeCache.get(path).length == 0) return 0;
            Path localPath = Path.of(path);
            try {
                String documentId;
                synchronized (dptLock) { documentId = documentCommand.upload(writeCache.remove(path), toRemote(localPath)); }
                synchronized (dptLock) { documentEntriesMap.put(localPath, documentCommand.documentInfo(documentId)); }
            } catch (IOException | InterruptedException e) {
                return ErrorCodes.EREMOTEIO();
            }
        }
        return 0;
    }

    @Override
    public int create(String path, long mode, FuseFileInfo fi) {
        Path localPath = Path.of(path);
        if (documentEntriesMap.containsKey(localPath)) return ErrorCodes.EEXIST();

        Path remotePath = toRemote(localPath);
        DocumentEntry documentEntry;
        try {
            synchronized (dptLock) { documentEntry = documentCommand.documentInfo(documentCommand.create(remotePath)); }
        } catch (IOException | InterruptedException e) {
            return ErrorCodes.EREMOTEIO();
        }

        documentEntriesMap.put(localPath, documentEntry);
        fileCache.put(path, new byte[0]);
        writeCache.put(path, new byte[0]);
        return 0;
    }

    @Override
    public int write(String path, Pointer buf, long size, long offset, FuseFileInfo fi) {

        byte[] content = writeCache.get(path);
        if (content.length < offset + size) {
            byte[] resized = new byte[(int) (offset + size)];
            System.arraycopy(content, 0, resized, 0, content.length);
            content = resized;
            writeCache.put(path, content);
        }
        buf.get(0, content, (int) offset, (int) size);

        documentEntriesMap.computeIfPresent(Path.of(path), (key, documentEntry) -> {
            documentEntry.setFileSize(documentEntry.getFileSize() + size);
            return documentEntry;
        });
        fileCache.put(path, content);

        return (int) size;
    }

    private int readFromCache(String path, Pointer buf, @size_t long size, @off_t long offset) {
        byte[] content = fileCache.get(path);
        int length = content.length;
        if (offset < length) {
            if (offset + size > length) {
                size = length - offset;
            }
            buf.put(0, content, (int) offset, (int) size);
        } else {
            size = 0;
        }
        return (int) size;
    }

    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        Path localPath = Path.of(path);
        if (!documentEntriesMap.containsKey(localPath)) return -ErrorCodes.ENOENT();

        DocumentEntry documentEntry = documentEntriesMap.get(localPath);
        Path remotePath = Path.of(documentEntry.getEntryPath());

        if (!fileCache.containsKey(path)) {
            synchronized (dptLock) {
                try (InputStream stream = documentCommand.download(remotePath)) {
                    byte[] content = IOUtils.toByteArray(stream);
                    fileCache.put(path, content);
                } catch (Exception ignored) { return ErrorCodes.EREMOTEIO(); }
            }
        }

        return readFromCache(path, buf, size, offset);
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
        filter.apply(buf, ".", null, 0);
        filter.apply(buf, "..", null, 0);

        Set<String> content = findFolderChildren(Path.of(path), documentEntriesMap);
        content.forEach(element -> filter.apply(buf, element, null, 0));
        return 0;
    }

    public Set<String> findFolderChildren(Path folder, Map<Path, DocumentEntry> documentEntriesMap) {
        Set<String> content = new HashSet<>();
        for (Path candidate : documentEntriesMap.keySet()) {
            if (candidate.startsWith(folder) && !candidate.equals(folder)) {
                content.add(folder.relativize(candidate).getName(0).toString());
            }
        }
        return content;
    }

    public void mountDpt(Path mountPoint) throws IOException, InterruptedException {
        logWriter.log("Mounting the Digital Paper on " + mountPoint);
        Files.createDirectories(mountPoint);
        DocumentListResponse documentListResponse;
        synchronized (dptLock) { documentListResponse = documentCommand.listDocuments(EntryType.ALL); }
        for (DocumentEntry entry : documentListResponse.getEntryList()) {
            Path path = toLocal(Path.of(entry.getEntryPath()));
            documentEntriesMap.put(path, entry);
        }
        try {
            mount(mountPoint, true, false);
        }
        finally {
            umount();
            logWriter.log("Digital Paper unmounted from " + mountPoint);
        }
    }

    @Override
    public int setxattr(String path, String name, Pointer value, long size, int flags) { return 0; }

    @Override
    public int chown(String path, long uid, long gid) { return 0; }

    @Override
    public int truncate(String path, long size) { return 0; }

    @Override
    public int utimens(String path, Timespec[] timespec) { return 0; }
}
