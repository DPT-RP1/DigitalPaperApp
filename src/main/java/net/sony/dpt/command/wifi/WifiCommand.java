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

    /**
     * data = self._get_endpoint('/system/configs/wifi_accesspoints').json()
     * for ap in data['aplist']:
     * ap['ssid'] = base64.b64decode(ap['ssid']).decode('utf-8', errors='replace')
     * return data['aplist']
     */
    public AccessPointList list() throws IOException, InterruptedException {
        String json = digitalPaperEndpoint.listWifi();
        return objectMapper.readValue(json, AccessPointList.class);
    }

}
