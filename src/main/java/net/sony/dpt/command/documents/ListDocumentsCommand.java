package net.sony.dpt.command.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sony.dpt.DigitalPaperEndpoint;

import java.io.IOException;

public class ListDocumentsCommand {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final DigitalPaperEndpoint digitalPaperEndpoint;

    public ListDocumentsCommand(DigitalPaperEndpoint digitalPaperEndpoint) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
    }

    public static DocumentListResponse fromJson(String json) throws IOException {
        return objectMapper.readValue(json, DocumentListResponse.class);
    }

    public DocumentListResponse listDocuments() throws IOException, InterruptedException {
        String json = digitalPaperEndpoint.listDocuments();
        return fromJson(json);
    }

    public DocumentListResponse listDocument(EntryType entryType) throws IOException, InterruptedException {
        String json = digitalPaperEndpoint.listDocuments(entryType);
        return fromJson(json);
    }

}
