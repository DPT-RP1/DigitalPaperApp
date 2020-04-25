package net.sony.dpt.command.wifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sony.dpt.network.DigitalPaperEndpoint;
import net.sony.util.InputReader;
import net.sony.util.LogWriter;

import java.io.IOException;
import java.util.Optional;

public class WifiCommand {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final DigitalPaperEndpoint digitalPaperEndpoint;
    private final InputReader inputReader;
    private final LogWriter logWriter;

    public WifiCommand(DigitalPaperEndpoint digitalPaperEndpoint, final InputReader inputReader, final LogWriter logWriter) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
        this.inputReader = inputReader;
        this.logWriter = logWriter;
    }

    public void delete(String SSID) throws IOException, InterruptedException {
        AccessPointList accessPointList = list();
        Optional<AccessPoint> accessPoint = accessPointList.getAccessPoints().stream().filter(ap -> ap.getDecodedSSID().equals(SSID)).findFirst();

        if (accessPoint.isPresent()) {
            digitalPaperEndpoint.removeWifi(accessPoint.get());
        } else {
            logWriter.log("Impossible to remove " + SSID);
        }
    }

    public void addInteractive(String SSID) throws IOException, InterruptedException {
        logWriter.log("Enter the password for " + SSID);
        String password = inputReader.read();
        add(SSID, password);
    }

    public void add(String SSID, String password) throws IOException, InterruptedException {
        AccessPointList accessPointList = scan();
        Optional<AccessPoint> accessPoint = accessPointList.getAccessPoints().stream().filter(ap -> ap.getDecodedSSID().equals(SSID)).findFirst();
        if (accessPoint.isPresent()) {
            AccessPoint found = accessPoint.get();

            found.setPassword(password);
            found.setDhcp(true);
            digitalPaperEndpoint.addWifi(
                    new AccessPointCreationRequest(found)
            );
        } else {
            logWriter.log(SSID + " is not currently visible.");
        }
    }

    public AccessPointList list() throws IOException, InterruptedException {
        String json = digitalPaperEndpoint.listWifi();
        return objectMapper.readValue(json, AccessPointList.class);
    }

    public AccessPointList scan() throws IOException, InterruptedException {
        String json = digitalPaperEndpoint.scanWifi();
        return objectMapper.readValue(json, AccessPointList.class);
    }

    public void state() throws IOException, InterruptedException {
        AccessPoint ap = digitalPaperEndpoint.wifiState();
        if (ap != null && "connected".equals(ap.getState())) {
            logWriter.log("Wifi connected to " + ap.getDecodedSSID());
        } else {
            logWriter.log("Wifi not connected");
        }
    }

    public void turnOn() throws IOException, InterruptedException {
        digitalPaperEndpoint.setWifiState(true);
    }

    public void turnOff() throws IOException, InterruptedException {
        digitalPaperEndpoint.setWifiState(false);
    }
}
