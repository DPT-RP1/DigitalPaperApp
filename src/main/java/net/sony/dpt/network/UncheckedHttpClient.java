package net.sony.dpt.network;

import net.sony.util.CryptographyUtils;
import net.sony.util.MimeMultipartData;
import net.sony.util.SSLFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static net.sony.util.JsonUtils.writeValueAsString;

public class UncheckedHttpClient implements SimpleHttpClient {

    private static Map<String, String> defaultHeaders;
    private static CookieManager cookieManager;
    private final HttpClient httpClient;

    private static final int TIMEOUT = 10000;

    private UncheckedHttpClient(SSLContext sslContext) {
        defaultHeaders = new HashMap<>();
        HttpClient.Builder builder = HttpClient
                .newBuilder()
                .cookieHandler(CookieHandler.getDefault());

        if (sslContext != null) builder = builder.sslContext(sslContext);

        httpClient = builder.build();
    }

    public UncheckedHttpClient() throws KeyManagementException, NoSuchAlgorithmException {
        defaultHeaders = new HashMap<>();
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

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new SecureRandom());

        SSLParameters sslParameters = new SSLParameters();
        // This should prevent host validation
        sslParameters.setEndpointIdentificationAlgorithm("");

        final Properties props = System.getProperties();
        props.setProperty("jdk.internal.httpclient.disableHostnameVerification", Boolean.TRUE.toString());


        httpClient = HttpClient
                .newBuilder()
                .cookieHandler(CookieHandler.getDefault())
                .sslContext(sslContext)
                .sslParameters(sslParameters)
                .build();
    }

    public static SimpleHttpClient insecure() {
        initCookieManager();
        return new UncheckedHttpClient(null);
    }

    public static SimpleHttpClient secure(String certPem, String privateKeyPem, CryptographyUtils cryptographyUtils) throws KeyManagementException, NoSuchAlgorithmException {
        initCookieManager();
        try {
            return new UncheckedHttpClient(new SSLFactory(certPem, privateKeyPem, cryptographyUtils).getSslContext());
        } catch (Exception e) {
            return secureNoHostVerification();
        }
    }

    public static SimpleHttpClient secureNoHostVerification() throws NoSuchAlgorithmException, KeyManagementException {
        initCookieManager();
        return new UncheckedHttpClient();
    }

    public static void initCookieManager() {
        if (cookieManager == null) {
            cookieManager = new CookieManager();
            CookieHandler.setDefault(cookieManager);
        }
    }



    public HttpRequest.Builder requestBuilder() {
        HttpRequest.Builder builder = HttpRequest.newBuilder();
        for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        return builder;
    }

    @Override
    public String get(String url) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder()
                .uri(URI.create(url))
                .method("GET", HttpRequest.BodyPublishers.ofString(""))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    @Override
    public HttpResponse<String> getWithResponse(String url) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder()
                .uri(URI.create(url))
                .method("GET", HttpRequest.BodyPublishers.ofString(""))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public String put(String url, Map<String, Object> jsonBody) throws IOException, InterruptedException {
        return putWithResponse(url, jsonBody).body();
    }

    @Override
    public String put(String url) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder()
                .uri(URI.create(url))
                .method("PUT", HttpRequest.BodyPublishers.ofString(""))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    @Override
    public void putCommonValue(String url, String rawBody) throws IOException, InterruptedException {
        Map<String, Object> commonValueParam = new HashMap<>() {{
            put("value", rawBody);
        }};
        put(url, commonValueParam);
    }

    @Override
    public void putWithResponse(String url, Object serializable) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder()
                .uri(URI.create(url))
                .method("PUT", HttpRequest.BodyPublishers.ofString(writeValueAsString(serializable)))
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public HttpResponse<String> putWithResponse(String url, Map<String, Object> jsonBody) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder()
                .uri(URI.create(url))
                .method("PUT", HttpRequest.BodyPublishers.ofString(writeValueAsString(jsonBody)))
                .timeout(Duration.ofMillis(TIMEOUT))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public String post(String url) throws IOException, InterruptedException {

        HttpRequest request = requestBuilder()
                .uri(URI.create(url))
                .method("POST", HttpRequest.BodyPublishers.ofString(""))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    @Override
    public String post(String url, Map<String, Object> jsonBody) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder()
                .uri(URI.create(url))
                .method("POST", HttpRequest.BodyPublishers.ofString(writeValueAsString(jsonBody)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    @Override
    public InputStream getFile(String url) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder()
                .uri(URI.create(url))
                .method("GET", HttpRequest.BodyPublishers.ofString(""))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream()).body();
    }

    @Override
    public void addDefaultHeader(String header, String value) {
        defaultHeaders.put(header, value);
    }

    @Override
    public void putFile(String url, Path localFile) throws IOException, InterruptedException {
        MimeMultipartData mimeMultipartData = MimeMultipartData.newBuilder()
                .withCharset(StandardCharsets.UTF_8)
                .addFile(localFile.getFileName().toString(), localFile, Files.probeContentType(localFile))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .header("Content-Type", mimeMultipartData.getContentType())
                .PUT(mimeMultipartData.getBodyPublisher())
                .uri(URI.create(url))
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    @Override
    public void putBytes(String url, String filename, String mimeType, byte[] content) throws IOException, InterruptedException {
        MimeMultipartData mimeMultipartData = MimeMultipartData.newBuilder()
                .withCharset(StandardCharsets.UTF_8)
                .addBlob(filename, filename, content, mimeType)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .header("Content-Type", mimeMultipartData.getContentType())
                .PUT(mimeMultipartData.getBodyPublisher())
                .uri(URI.create(url))
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    @Override
    public void delete(String url) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .method("DELETE", HttpRequest.BodyPublishers.ofString(""))
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }
}