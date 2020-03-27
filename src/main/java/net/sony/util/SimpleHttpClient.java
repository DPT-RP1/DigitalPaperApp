package net.sony.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class SimpleHttpClient {

    private static ObjectMapper mapper = new ObjectMapper();

    private HttpClient httpClient;

    public SimpleHttpClient() {
        httpClient = HttpClient.newHttpClient();
    }

    public String put(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("PUT", HttpRequest.BodyPublishers.ofString(""))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    public String post(String url) throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("POST", HttpRequest.BodyPublishers.ofString(""))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    public String post(String url, Map<String, String> jsonBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("POST", HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(jsonBody)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    public static Map<String, String> fromJSON(String json) throws IOException {
        return mapper.readValue(json, Map.class);
    }

}
