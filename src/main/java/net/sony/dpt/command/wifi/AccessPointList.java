package net.sony.dpt.command.wifi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AccessPointList {

    @JsonProperty("aplist")
    private List<AccessPoint> accessPoints;

    public List<AccessPoint> getAccessPoints() {
        return accessPoints;
    }

    public void setAccessPoints(List<AccessPoint> accessPoints) {
        this.accessPoints = accessPoints;
    }
}
