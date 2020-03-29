package net.sony.dpt.command.wifi;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AccessPoint {

    private boolean dhcp;
    private boolean proxy;
    private String security;
    private String ssid;

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
    }

    @JsonIgnore
    public String decodedSSID() {
        return new String(Base64.getDecoder().decode(ssid), StandardCharsets.UTF_8);
    }
}
