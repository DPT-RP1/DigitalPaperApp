package net.sony.dpt.command.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sony.dpt.DigitalPaperEndpoint;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

import static net.sony.util.SimpleHttpClient.fromJSON;

public class DocumentCommand {

    private static final Path REMOTE_ROOT = Path.of("Document");
    private final DigitalPaperEndpoint digitalPaperEndpoint;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public DocumentCommand(DigitalPaperEndpoint digitalPaperEndpoint) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
    }

    public InputStream download(Path remotePath) throws IOException, InterruptedException {
        remotePath = resolveRemotePath(remotePath);
        String remoteId = digitalPaperEndpoint.resolveObjectByPath(remotePath);

        return digitalPaperEndpoint.downloadByRemoteId(remoteId);
    }

    public void delete(Path path) throws IOException, InterruptedException {
        path = resolveRemotePath(path);
        String remoteId = digitalPaperEndpoint.resolveObjectByPath(path);
        if (remoteId != null) {
            digitalPaperEndpoint.deleteByDocumentId(remoteId);
        }
    }

    public String createFolderRecursively(Path folderPath) throws IOException, InterruptedException {
        String parentId = "root";
        String currentId = null;
        Path base = Path.of("");
        for (Path subDirectory : folderPath) {
            base = base.resolve(subDirectory);
            currentId = digitalPaperEndpoint.resolveObjectByPath(base);
            if (currentId == null) {
                currentId = digitalPaperEndpoint.createDirectory(base, parentId);
            }
            parentId = currentId;
        }
        return currentId;
    }

    public void deleteFolder(Path remotePath) throws IOException, InterruptedException {
        remotePath = resolveRemotePath(remotePath);
        String remoteId = digitalPaperEndpoint.resolveObjectByPath(remotePath);
        digitalPaperEndpoint.deleteFolderByRemoteId(remoteId);
    }

    public String create(Path remotePath) throws IOException, InterruptedException {
        Path directory = remotePath.getParent();

        String parentId = createFolderRecursively(directory);
        return digitalPaperEndpoint.touchFile(remotePath.getFileName().toString(), parentId);
    }

    public String upload(Path localPath, Path remotePath) throws IOException, InterruptedException {
        delete(remotePath);
        Path directory = remotePath.getParent();

        String parentId = createFolderRecursively(directory);
        return digitalPaperEndpoint.uploadFile(localPath, parentId);
    }

    public String upload(byte[] content, Path remotePath) throws IOException, InterruptedException {
        delete(remotePath);
        Path directory = remotePath.getParent();

        String parentId = createFolderRecursively(directory);
        return digitalPaperEndpoint.uploadFile(remotePath.getFileName().toString(), content, parentId);
    }

    private Path resolveRemotePath(Path remotePath) {
        if (!remotePath.startsWith(REMOTE_ROOT)) {
            return REMOTE_ROOT.resolve(remotePath);
        }
        return remotePath;
    }

    public String move(Path from, Path to) throws IOException, InterruptedException {
        from = resolveRemotePath(from);
        to = resolveRemotePath(to);
        String oldId = digitalPaperEndpoint.resolveObjectByPath(from);

        String newParentFolderId;
        // We assume here we'll only transfer extension-suffixed files
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.*");
        newParentFolderId = createFolderRecursively(to.getParent());
        if (!matcher.matches(to.getFileName())) {
            // We may just want to mv a folder
            if (!matcher.matches(from.getFileName())) {
                digitalPaperEndpoint.updateFolder(oldId, newParentFolderId, to.getName(to.getNameCount() - 1).toString());
                return newParentFolderId;
            } else {
                newParentFolderId = createFolderRecursively(to);
                to = to.resolve(from.getFileName());
            }
        }

        String newFileName = null;
        if (!from.getFileName().equals(to.getFileName())) {
            newFileName = to.getFileName().toString();
        }
        digitalPaperEndpoint.setFileInfo(oldId, newParentFolderId, newFileName);
        return newParentFolderId;
    }

    public void copy(Path from, Path to) throws IOException, InterruptedException {
        from = resolveRemotePath(from);
        to = resolveRemotePath(to);

        String fromId = digitalPaperEndpoint.resolveObjectByPath(from);

        String toFolder;
        // We assume here we'll only transfer extension-suffixed files
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.*");
        if (matcher.matches(to.getFileName())) {
            // We have a file
            toFolder = createFolderRecursively(to.getParent());
        } else {
            // We have a folder
            toFolder = createFolderRecursively(to);
            to = to.resolve(from.getFileName());
        }

        String toFilename = null;
        if (!from.getFileName().equals(to.getFileName())) {
            toFilename = to.getFileName().toString();
        }

        digitalPaperEndpoint.copy(fromId, toFolder, toFilename);
    }

    public DocumentListResponse listContent(String folderId) throws IOException, InterruptedException {
        return fromJson(digitalPaperEndpoint.getFolderContent(folderId));
    }

    public DocumentListResponse listDocuments() throws IOException, InterruptedException {
        String json = digitalPaperEndpoint.listDocuments();
        return fromJson(json);
    }

    public DocumentListResponse listDocuments(EntryType entryType) throws IOException, InterruptedException {
        String json = digitalPaperEndpoint.listDocuments(entryType);
        return fromJson(json);
    }

    public DocumentEntry documentInfo(Path remotePath) throws IOException, InterruptedException {
        String documentId = digitalPaperEndpoint.resolveObjectByPath(remotePath);
        return documentInfo(documentId);
    }


    public DocumentEntry documentInfo(String documentId) throws IOException, InterruptedException {
        return fromJSON(digitalPaperEndpoint.documentInfos(documentId), DocumentEntry.class);
    }

    public static DocumentListResponse fromJson(String json) throws IOException {
        return objectMapper.readValue(json, DocumentListResponse.class);
    }
}
