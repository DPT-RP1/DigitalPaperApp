package net.sony.dpt.command.firmware;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FirmwareVersionResponse {
    private String value;

    @JsonProperty("model_name")
    private String modelName;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
}
