package net.sony.dpt.command.documents;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class DocumentListResponse {

    private long count;

    @JsonProperty("entry_list")
    private List<DocumentEntry> entryList;

    @JsonProperty("entry_list_hash")
    private String entryListHash;

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public List<DocumentEntry> getEntryList() {
        return entryList;
    }

    public void setEntryList(List<DocumentEntry> entryList) {
        this.entryList = entryList;
    }

    public String getEntryListHash() {
        return entryListHash;
    }

    public void setEntryListHash(String entryListHash) {
        this.entryListHash = entryListHash;
    }
}
