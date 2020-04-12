package net.sony.dpt.fuse;

import jnr.ffi.Pointer;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import net.sony.dpt.command.documents.DocumentEntry;
import net.sony.dpt.command.documents.DocumentListResponse;
import net.sony.dpt.command.documents.DocumentCommand;
import net.sony.dpt.command.documents.EntryType;
import org.apache.commons.io.IOUtils;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DptFuseMounter extends FuseStubFS {

    private static final Path REMOTE_ROOT = Path.of("Document/");
    private static final Path LOCAL_ROOT = Path.of("/");

    private final DocumentCommand listDocumentsCommand;
    private final DocumentCommand documentCommand;

    private static Path toRemote(Path localPath) {
        return REMOTE_ROOT.resolve(LOCAL_ROOT.relativize(localPath));
    }

    private static Path toLocal(Path remotePath) {
        return LOCAL_ROOT.resolve(REMOTE_ROOT.relativize(remotePath));
    }

    public DptFuseMounter(final DocumentCommand listDocumentsCommand, final DocumentCommand documentCommand) {
        this.listDocumentsCommand = listDocumentsCommand;
        this.documentCommand = documentCommand;

        fileCache = new HashMap<>();
    }

    private int getFolderAttr(FileStat stat) {
        stat.st_mode.set(FileStat.S_IFDIR | 0755);
        stat.st_nlink.set(2);
        return 0;
    }

    private int getFileAttr(DocumentEntry found, FileStat stat) {
        stat.st_mode.set(FileStat.S_IFREG | 0444);
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
        if (!documentEntriesMap.containsKey(Path.of(path))) return -ErrorCodes.ENOENT();
        return 0;
    }

    @Override
    public int mkdir(String path, long mode) {
        Path localPath = Path.of(path);

        if (documentEntriesMap.containsKey(localPath)) return -ErrorCodes.EEXIST();
        try {
            documentCommand.createFolderRecursively(toRemote(localPath));
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
            documentCommand.deleteFolder(toRemote(localPath));
        } catch (IOException | InterruptedException e) {
            return -ErrorCodes.EREMOTEIO();
        }
        Set<Path> toRemove = documentEntriesMap.keySet()
                .stream()
                .filter(candidate -> candidate.startsWith(localPath))
                .collect(Collectors.toSet());
        toRemove.forEach(path1 -> documentEntriesMap.remove(path1));
        return 0;
    }

    // This cache will help to read partial files TODO: definitely a good LRU use case !!
    private final Map<String, byte[]> fileCache;
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
            try (InputStream stream = documentCommand.download(remotePath)){
                byte[] content = IOUtils.toByteArray(stream);
                fileCache.put(path, content);
            } catch (Exception ignored) { }
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

    private Map<Path, DocumentEntry> documentEntriesMap;
    public void buildDocumentsMap(List<DocumentEntry> documentEntries) {
        documentEntriesMap = new HashMap<>();

        for (DocumentEntry entry : documentEntries) {
            Path path = toLocal(Path.of(entry.getEntryPath()));
            documentEntriesMap.put(path, entry);
        }
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
        Files.createDirectories(mountPoint);
        DocumentListResponse documentListResponse = listDocumentsCommand.listDocuments(EntryType.ALL);
        buildDocumentsMap(documentListResponse.getEntryList());
        try {
            mount(mountPoint, true, true);
        } finally {
            umount();
        }
    }
}
