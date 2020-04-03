package net.sony.dpt.command.documents;

import net.sony.dpt.DigitalPaperEndpoint;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

public class TransferDocumentCommand {

    private final DigitalPaperEndpoint digitalPaperEndpoint;

    public TransferDocumentCommand(DigitalPaperEndpoint digitalPaperEndpoint) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
    }

    public InputStream download(Path remotePath) throws IOException, InterruptedException {
        remotePath = resolveRemotePath(remotePath);
        String remoteId = digitalPaperEndpoint.resolveObjectByPath(remotePath);

        InputStream file = digitalPaperEndpoint.downloadByRemoteId(remoteId);
        try (ByteArrayOutputStream memoryCopy = new ByteArrayOutputStream()) {
            IOUtils.copy(file, memoryCopy);
            file.close();
            return new ByteArrayInputStream(memoryCopy.toByteArray());
        }
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

    public String upload(Path localPath, Path remotePath) throws IOException, InterruptedException {
        delete(remotePath);
        Path directory = remotePath.getParent();

        String parentId = createFolderRecursively(directory);
        return digitalPaperEndpoint.uploadFile(localPath, parentId);
    }

    private static final Path REMOTE_ROOT = Path.of("Document");
    private Path resolveRemotePath(Path remotePath) {
        if (!remotePath.startsWith(REMOTE_ROOT)) {
            return REMOTE_ROOT.resolve(remotePath);
        }
        return remotePath;
    }

    public void move(Path from, Path to) throws IOException, InterruptedException {
        from = resolveRemotePath(from);
        to = resolveRemotePath(to);
        String oldId = digitalPaperEndpoint.resolveObjectByPath(from);

        String newParentFolderId;
        // We assume here we'll only transfer extension-suffixed files
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.*");
        if (matcher.matches(to.getFileName())) {
            // We have a file
            newParentFolderId = createFolderRecursively(to.getParent());
        } else {
            // We have a folder
            newParentFolderId = createFolderRecursively(to);
            to = to.resolve(from.getFileName());
        }

        String newFileName = null;
        if (!from.getFileName().equals(to.getFileName())) {
            newFileName = to.getFileName().toString();
        }
        digitalPaperEndpoint.setFileInfo(oldId, newParentFolderId, newFileName);
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
}
