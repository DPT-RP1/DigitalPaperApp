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

    public boolean exists(Path path) throws IOException, InterruptedException {
        return digitalPaperEndpoint.resolveObjectByPath(path) != null;
    }

    public String createFolderRecursively(Path folderPath) throws IOException, InterruptedException {
        String parentId = digitalPaperEndpoint.resolveObjectByPath(Path.of("/"));
        for (Path subDirectory : folderPath) {
            if (!exists(subDirectory)) {
                parentId = digitalPaperEndpoint.createDirectory(subDirectory, parentId);
            }
        }
        return parentId;
    }

    public void upload(InputStream file, Path remotePath) throws IOException, InterruptedException {
        delete(remotePath);
        Path fileName = remotePath.getFileName();
        Path directory = remotePath.getParent();

        createFolderRecursively(directory);

    }

}
