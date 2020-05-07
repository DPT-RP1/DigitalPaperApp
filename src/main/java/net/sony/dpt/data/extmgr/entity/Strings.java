package net.sony.dpt.data.extmgr.entity;

public class Strings {
    private String entryId;
    private String localeString;
    private String stringValue;

    public Strings() {
    }

    public Strings(String entryId, String stringValue) {
        this.entryId = entryId;
        this.stringValue = stringValue;
    }

    public Strings(String entryId, String localeString, String stringValue) {
        this.entryId = entryId;
        this.stringValue = stringValue;
        this.localeString = localeString;
    }

    public String getEntryId() {
        return entryId;
    }

    public void setEntryId(String entryId) {
        this.entryId = entryId;
    }

    public String getLocaleString() {
        return localeString;
    }

    public void setLocaleString(String localeString) {
        this.localeString = localeString;
    }

    public String getStringValue() {
        return stringValue;
    }

    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
    }
}
