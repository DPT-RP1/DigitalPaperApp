package net.sony.dpt.command.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sony.dpt.network.DigitalPaperEndpoint;
import net.sony.util.LogWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static net.sony.util.JsonUtils.fromJSON;

public class SystemConfigCommand {

    private final DigitalPaperEndpoint digitalPaperEndpoint;
    private final LogWriter logWriter;

    private final ObjectMapper objectMapper;

    public SystemConfigCommand(final DigitalPaperEndpoint digitalPaperEndpoint, final LogWriter logWriter) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
        this.logWriter = logWriter;
        objectMapper = new ObjectMapper();
    }

    public String getOwnerName() throws IOException, InterruptedException {
        return digitalPaperEndpoint.getOwnerName();
    }

    public void setOwnerName(String name) throws IOException, InterruptedException {
        digitalPaperEndpoint.setOwnerName(name);
    }

    public void showOwnerName() throws IOException, InterruptedException {
        logWriter.log("Configured owner name: " + getOwnerName());
    }

    public BatteryStatus getBatteryStatus() throws IOException, InterruptedException {
        return fromJSON(digitalPaperEndpoint.getBatteryStatus(), BatteryStatus.class);
    }

    public void showBatteryStatus() throws IOException, InterruptedException {
        logWriter.log(getBatteryStatus().toString());
    }

    public StorageStatus getStorageStatus() throws IOException, InterruptedException {
        return fromJSON(digitalPaperEndpoint.getStorageStatus(), StorageStatus.class);
    }

    public void showStorageStatus() throws IOException, InterruptedException {
        logWriter.log(getStorageStatus().toString());
    }

    public String getSystemConfigs() throws IOException, InterruptedException {
        return digitalPaperEndpoint.getSystemConfigs();
    }

    public void saveSystemConfigsToLocal(Path target) throws IOException, InterruptedException {
        String systemConfig = getSystemConfigs();
        logWriter.log("System configuration: ");
        logWriter.log(systemConfig);
        Files.writeString(target, systemConfig);
    }

    @SuppressWarnings("unchecked")
    public void setSystemConfigs(String json) throws IOException, InterruptedException {
        Map<String, Object> configMap = (Map<String, Object>) objectMapper.readValue(json, Map.class);
        for (Map.Entry<String, Object> entry : configMap.entrySet()) {
            digitalPaperEndpoint.setSystemConfig(entry.getKey(), (Map<String, Object> )entry.getValue());
        }
    }

    public void sendSystemConfigsToRemote(Path source) throws IOException, InterruptedException {
        String json = Files.readString(source);
        setSystemConfigs(json);
    }
}
