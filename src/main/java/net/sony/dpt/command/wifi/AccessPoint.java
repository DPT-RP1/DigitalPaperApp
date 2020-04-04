package net.sony.dpt.command.wifi;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AccessPoint {

    private boolean dhcp;
    private boolean proxy;
    private String security;
    private String ssid;

    @JsonProperty("frequency_band")
    private String frequencyBand;
    @JsonProperty("rssi_level")
    private String rssiLevel;
    @JsonProperty("state")
    private String state;

    @JsonProperty("passwd")
    private String password;

    @JsonIgnore
    private String decodedSSID;

    public boolean isDhcp() {
        return dhcp;
    }

    public void setDhcp(boolean dhcp) {
        this.dhcp = dhcp;
    }

    public boolean isProxy() {
        return proxy;
    }

    public void setProxy(boolean proxy) {
        this.proxy = proxy;
    }

    public String getSecurity() {
        return security;
    }

    public void setSecurity(String security) {
        this.security = security;
    }

    public String getSsid() {
        return ssid;
    }

    public void setSsid(String ssid) {
        this.ssid = ssid;
        this.decodedSSID = new String(Base64.getDecoder().decode(ssid), StandardCharsets.UTF_8);
    }

    @JsonIgnore
    public String getDecodedSSID() {
        return decodedSSID;
    }


    public void setPassword(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
