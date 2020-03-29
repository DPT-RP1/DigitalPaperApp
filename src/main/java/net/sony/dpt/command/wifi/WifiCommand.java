package net.sony.dpt.command.wifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sony.dpt.DigitalPaperEndpoint;

import java.io.IOException;

public class WifiCommand {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private DigitalPaperEndpoint digitalPaperEndpoint;

    public WifiCommand(DigitalPaperEndpoint digitalPaperEndpoint) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
    }

    public AccessPointList list() throws IOException, InterruptedException {
        String json = digitalPaperEndpoint.listWifi();
        return objectMapper.readValue(json, AccessPointList.class);
    }

    /**
     * def wifi_scan(self):
     * data = self._post_endpoint('/system/controls/wifi_accesspoints/scan').json()
     * for ap in data['aplist']:
     * ap['ssid'] = base64.b64decode(ap['ssid']).decode('utf-8', errors='replace')
     * return data['aplist']
     */
    public AccessPointList scan() throws IOException, InterruptedException {
        String json = digitalPaperEndpoint.scanWifi();
        return objectMapper.readValue(json, AccessPointList.class);
    }

}
