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
        String remoteId = digitalPaperEndpoint.resolveObjectByPath(remotePath);

        InputStream file = digitalPaperEndpoint.downloadByRemoteId(remoteId);
        try (ByteArrayOutputStream memoryCopy = new ByteArrayOutputStream()) {
            IOUtils.copy(file, memoryCopy);
            file.close();
            return new ByteArrayInputStream(memoryCopy.toByteArray());
        }
    }

    public void delete(Path path) throws IOException, InterruptedException {
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
        String remoteId = digitalPaperEndpoint.resolveObjectByPath(remotePath);
        digitalPaperEndpoint.deleteFolderByRemoteId(remoteId);
    }

    public String upload(Path localPath, Path remotePath) throws IOException, InterruptedException {
        delete(remotePath);
        Path directory = remotePath.getParent();

        String parentId = createFolderRecursively(directory);
        return digitalPaperEndpoint.uploadFile(localPath, parentId);
    }

    public void moveDocument(Path oldPath, Path newPath) throws IOException, InterruptedException {
        String oldId = digitalPaperEndpoint.resolveObjectByPath(oldPath);

        String newParentFolderId;
        // We assume here we'll only transfer extension-suffixed files
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.*");
        if (matcher.matches(newPath.getFileName())) {
            // We have a file
            newParentFolderId = createFolderRecursively(newPath.getParent());
        } else {
            // We have a folder
            newParentFolderId = createFolderRecursively(newPath);
            newPath = newPath.resolve(oldPath.getFileName());
        }

        String newFileName = null;
        if (!oldPath.getFileName().equals(newPath.getFileName())) {
            newFileName = newPath.getFileName().toString();
        }
        digitalPaperEndpoint.setFileInfo(oldId, newParentFolderId, newFileName);
    }

}
