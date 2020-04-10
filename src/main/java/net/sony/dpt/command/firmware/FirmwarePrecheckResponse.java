package net.sony.dpt.command.firmware;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {
 *   "image_file": "string",
 *   "battery": "string"
 * }
 */
public class FirmwarePrecheckResponse {

    @JsonProperty("image_file")
    private String imageFile;
    private String battery;

    public String getImageFile() {
        return imageFile;
    }

    public void setImageFile(String imageFile) {
        this.imageFile = imageFile;
    }

    public String getBattery() {
        return battery;
    }

    public void setBattery(String battery) {
        this.battery = battery;
    }

    @Override
    public String toString() {
        return "FirmwarePrecheckResponse{" +
                "imageFile='" + imageFile + '\'' +
                ", battery='" + battery + '\'' +
                '}';
    }

    private final String OK = "ok";
    public boolean validate() {
        return OK.equals(battery) && OK.equals(imageFile);
    }
}
