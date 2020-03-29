package net.sony.dpt.command.wifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sony.dpt.DigitalPaperEndpoint;

import java.io.IOException;

public class WifiCommand {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final DigitalPaperEndpoint digitalPaperEndpoint;

    public WifiCommand(DigitalPaperEndpoint digitalPaperEndpoint) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
    }

    public AccessPointList list() throws IOException, InterruptedException {
        String json = digitalPaperEndpoint.listWifi();
        return objectMapper.readValue(json, AccessPointList.class);
    }

    public AccessPointList scan() throws IOException, InterruptedException {
        String json = digitalPaperEndpoint.scanWifi();
        return objectMapper.readValue(json, AccessPointList.class);
    }

}
