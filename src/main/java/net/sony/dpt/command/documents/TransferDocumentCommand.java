package net.sony.dpt.command.documents;

import net.sony.dpt.DigitalPaperEndpoint;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class TransferDocumentCommand {

    private DigitalPaperEndpoint digitalPaperEndpoint;

    public TransferDocumentCommand(DigitalPaperEndpoint digitalPaperEndpoint) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
    }

    public InputStream download(String remoteId) throws IOException, InterruptedException {
        InputStream file = digitalPaperEndpoint.downloadByRemoteId(remoteId);
        ByteArrayOutputStream memoryCopy = new ByteArrayOutputStream();

        IOUtils.copy(file, memoryCopy);
        return new ByteArrayInputStream(memoryCopy.toByteArray());
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

    public String upload(Path localPath, Path remotePath) throws IOException, InterruptedException {
        delete(remotePath);
        Path directory = remotePath.getParent();

        String parentId = createFolderRecursively(directory);
        return digitalPaperEndpoint.uploadFile(localPath, parentId);
    }

}
