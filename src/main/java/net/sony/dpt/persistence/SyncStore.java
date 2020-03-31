package net.sony.dpt.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

public class SyncStore {

    private static final Path applicationPath = Path.of(".dpt");
    private static final Path lastSyncPath = Path.of("last_sync.date");
    private final Path storagePath;

    public SyncStore(Path storagePath) {
        this.storagePath = storagePath.resolve(applicationPath);
    }

    public void storeLastSyncDate(Date lastSync) throws IOException {
        try {
            Files.createFile(storagePath.resolve(lastSyncPath));
        } catch (IOException ignored) {
        }

        Files.write(storagePath.resolve(lastSyncPath), String.valueOf(lastSync.getTime()).getBytes(StandardCharsets.UTF_8));
    }

    public Date retrieveLastSyncDate() {
        try {
            String epochString = Files.readString(storagePath.resolve(lastSyncPath));
            return new Date(Long.parseLong(epochString.strip()));
        } catch (IOException e) {
            return null;
        }

    }
}
