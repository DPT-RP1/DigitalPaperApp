package net.sony.dpt.command.documents;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EntryType {
    ALL("all"),
    DOCUMENT("document");

    private final String value;

    EntryType(String value) {
        this.value = value;
    }

    @JsonValue
    public String toString() {
        return value;
    }
}
