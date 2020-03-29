package net.sony.dpt;

import net.sony.dpt.command.documents.EntryType;
import net.sony.util.SimpleHttpClient;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static net.sony.util.SimpleHttpClient.fromJSON;

public class DigitalPaperEndpoint {

    private static int PORT = 8443;
    private String baseUrl;
    private SimpleHttpClient simpleHttpClient;

    public DigitalPaperEndpoint(String addr, SimpleHttpClient simpleHttpClient) {
        this.baseUrl = "https://" + addr + ":" + PORT;
        this.simpleHttpClient = simpleHttpClient;
    }

    public String getNonce(String clientId) throws IOException, InterruptedException {
        return fromJSON(simpleHttpClient.get(baseUrl + "/auth/nonce/" + clientId)).get("nonce");
    }

    public String listDocuments() throws IOException, InterruptedException {
        return simpleHttpClient.get(baseUrl + "/documents2");
    }

    public String listDocuments(EntryType entryType) throws IOException, InterruptedException {
        return simpleHttpClient.get(baseUrl + "/documents2?entry_type=" + entryType);
    }

    public URI getURI() throws URISyntaxException {
        return new URI(baseUrl);
    }

}
