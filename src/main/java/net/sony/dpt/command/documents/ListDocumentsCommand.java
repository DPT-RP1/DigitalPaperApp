package net.sony.dpt.command.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sony.dpt.DigitalPaperEndpoint;

import java.io.IOException;

public class ListDocumentsCommand {

    private DigitalPaperEndpoint digitalPaperEndpoint;

    private ObjectMapper objectMapper;

    public ListDocumentsCommand(DigitalPaperEndpoint digitalPaperEndpoint) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;

        this.objectMapper = new ObjectMapper();
    }

    public DocumentListResponse listDocuments() throws IOException, InterruptedException {
        String json = digitalPaperEndpoint.listDocuments();
        return objectMapper.readValue(json, DocumentListResponse.class);
    }

}
