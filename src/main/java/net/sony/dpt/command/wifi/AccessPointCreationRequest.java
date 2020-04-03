package net.sony.dpt.command.wifi;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * {
 *   "ssid": "string",
 *   "security": "string",
 *   "passwd": "string",
 *   "dhcp": "string",
 *   "static_address": "string",
 *   "gateway": "string",
 *   "network_mask": "string",
 *   "dns1": "string",
 *   "dns2": "string",
 *   "proxy": "string",
 *   "proxy_host": "string",
 *   "proxy_port": "string",
 *   "eap": "string",
 *   "eap_phase2": "string",
 *   "eap_id": "string",
 *   "eap_anid": "string",
 *   "eap_cacert": "string",
 *   "eap_cert": "string"
 * }
 */
public class AccessPointCreationRequest {

    private boolean dhcp;
    private boolean proxy;
    private String security;
    private String ssid;

    @JsonProperty("passwd")
    private String password;

    public AccessPointCreationRequest() {

    }

    public AccessPointCreationRequest(AccessPoint accessPoint) {
        this.dhcp = accessPoint.isDhcp();
        this.proxy = accessPoint.isProxy();
        this.password = accessPoint.getPassword();
        this.security = accessPoint.getSecurity();
        this.ssid = accessPoint.getSsid();
    }

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

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }
}
