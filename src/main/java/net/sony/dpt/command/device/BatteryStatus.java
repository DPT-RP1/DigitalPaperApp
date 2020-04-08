package net.sony.dpt.command.device;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BatteryStatus {
    private String level;

    @JsonProperty("icon_type")
    private String iconType;

    private String status;

    private String health;

    private String plugged;

    private String pen;

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getIconType() {
        return iconType;
    }

    public void setIconType(String iconType) {
        this.iconType = iconType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getHealth() {
        return health;
    }

    public void setHealth(String health) {
        this.health = health;
    }

    public String getPlugged() {
        return plugged;
    }

    public void setPlugged(String plugged) {
        this.plugged = plugged;
    }

    public String getPen() {
        return pen;
    }

    public void setPen(String pen) {
        this.pen = pen;
    }

    @Override
    public String toString() {
        return "BatteryStatus{" +
                "level='" + level + '\'' +
                ", iconType='" + iconType + '\'' +
                ", status='" + status + '\'' +
                ", health='" + health + '\'' +
                ", plugged='" + plugged + '\'' +
                ", pen='" + pen + '\'' +
                '}';
    }
}
