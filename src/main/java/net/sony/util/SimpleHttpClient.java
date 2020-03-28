package net.sony.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Properties;

public class SimpleHttpClient {

    private static ObjectMapper mapper = new ObjectMapper();

    private HttpClient httpClient;
    private SSLContext sslContext;
    private SSLParameters sslParameters;

    public SimpleHttpClient() throws KeyManagementException, NoSuchAlgorithmException {
        disableSsl();
        CookieHandler.setDefault(new CookieManager());
        httpClient = HttpClient.newBuilder()
                .cookieHandler(CookieHandler.getDefault())
                .sslContext(sslContext)
                .sslParameters(sslParameters)
                .build();
    }

    public String get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("GET", HttpRequest.BodyPublishers.ofString(""))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    public String put(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("PUT", HttpRequest.BodyPublishers.ofString(""))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    public String put(String url, Map<String, String> jsonBody) throws IOException, InterruptedException {
        return putWithResponse(url, jsonBody).body();
    }

    public HttpResponse<String> putWithResponse(String url, Map<String, String> jsonBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("PUT", HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(jsonBody)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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

    private void disableSsl() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {

                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                }
        };

        sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new SecureRandom());

        sslParameters = new SSLParameters();
        // This should prevent host validation
        sslParameters.setEndpointIdentificationAlgorithm("");

        final Properties props = System.getProperties();
        props.setProperty("jdk.internal.httpclient.disableHostnameVerification", Boolean.TRUE.toString());
    }

}
