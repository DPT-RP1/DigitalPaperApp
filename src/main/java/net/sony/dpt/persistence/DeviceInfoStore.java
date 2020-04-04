package net.sony.dpt.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class DeviceInfoStore {
    private static final Path applicationPath = Path.of(".dpt");
    private static final Path lastSyncPath = Path.of("last_connection.ip");
    private final Path storagePath;

    public DeviceInfoStore(Path storagePath) {
        this.storagePath = storagePath.resolve(applicationPath);
    }

    public void storeLastIp(String lastIp) throws IOException {
        Files.createDirectories(storagePath);
        try {
            Files.createFile(storagePath.resolve(lastSyncPath));
        } catch (IOException ignored) {
        }
        Files.write(storagePath.resolve(lastSyncPath), lastIp.getBytes(StandardCharsets.UTF_8));
    }

    public String retrieveLastIp() {
        try {
            return Files.readString(storagePath.resolve(lastSyncPath));
        } catch (IOException e) {
            return null;
        }

    }
}
