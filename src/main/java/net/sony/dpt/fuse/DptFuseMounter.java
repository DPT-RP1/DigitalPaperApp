package net.sony.dpt.fuse;

import jnr.ffi.Pointer;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import net.sony.dpt.command.documents.DocumentEntry;
import net.sony.dpt.command.documents.DocumentListResponse;
import net.sony.dpt.command.documents.ListDocumentsCommand;
import net.sony.dpt.command.documents.TransferDocumentCommand;
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

public class DptFuseMounter extends FuseStubFS {

    private static final Path REMOTE_ROOT = Path.of("Document/");
    private static final Path LOCAL_ROOT = Path.of("/");

    private final ListDocumentsCommand listDocumentsCommand;
    private final TransferDocumentCommand transferDocumentCommand;

    public DptFuseMounter(final ListDocumentsCommand listDocumentsCommand, final TransferDocumentCommand transferDocumentCommand) {
        this.listDocumentsCommand = listDocumentsCommand;
        this.transferDocumentCommand = transferDocumentCommand;

        fileCache = new HashMap<>();
    }

    @Override
    public int getattr(String path, FileStat stat) {
        int res = 0;
        Path searchPath = Path.of(path);
        if (!documentEntriesMap.containsKey(searchPath)) {
            // We could have either a directory or a completely wrong path
            if (LOCAL_ROOT.equals(searchPath) || folders.contains(searchPath)) {
                stat.st_mode.set(FileStat.S_IFDIR | 0755);
                stat.st_nlink.set(2);
                return res;
            }
            return -ErrorCodes.ENOENT();
        }

        DocumentEntry found = documentEntriesMap.get(searchPath);
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
        return res;
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        if (!documentEntriesMap.containsKey(Path.of(path))) return -ErrorCodes.ENOENT();
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
            try (InputStream stream = transferDocumentCommand.download(remotePath)){
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
    private Set<Path> folders;
    public void buildDocumentsMap(List<DocumentEntry> documentEntries) {
        documentEntriesMap = new HashMap<>();
        folders = new HashSet<>();

        for (DocumentEntry entry : documentEntries) {
            Path path = LOCAL_ROOT.resolve(
                    REMOTE_ROOT.relativize(Path.of(entry.getEntryPath()))
            );
            for (int i = 1; i < path.getNameCount(); i++) {
                folders.add(
                        LOCAL_ROOT.resolve(path.subpath(0, i))
                );
            }
            documentEntriesMap.put(path, entry);
        }
    }

    public Set<String> findFolderChildren(Path folder, Map<Path, DocumentEntry> documentEntriesMap) {
        Set<String> content = new HashSet<>();
        for (Path candidate : documentEntriesMap.keySet()) {
            if (candidate.startsWith(folder)) {
                content.add(folder.relativize(candidate).getName(0).toString());
            }
        }
        return content;
    }

    public void mountDpt(Path mountPoint) throws IOException, InterruptedException {
        Files.createDirectories(mountPoint);
        DocumentListResponse documentListResponse = listDocumentsCommand.listDocuments();
        buildDocumentsMap(documentListResponse.getEntryList());
        try {
            mount(mountPoint, true, true);
        } finally {
            umount();
        }
    }
}
