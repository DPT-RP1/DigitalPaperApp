package net.sony.dpt;

import net.sony.util.SimpleHttpClient;

import java.io.IOException;

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

}
