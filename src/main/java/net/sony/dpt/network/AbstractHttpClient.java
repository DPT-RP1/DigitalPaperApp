package net.sony.dpt.network;

import net.sony.util.MimeMultipartData;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractHttpClient implements SimpleHttpClient {

    protected static Map<String, String> defaultHeaders;

    public AbstractHttpClient() {
        defaultHeaders = new HashMap<>();
    }

    @Override
    public String get(String url) throws IOException, InterruptedException {
        return getWithResponse(url).body();
    }

    @Override
    public String put(String url, Map<String, Object> jsonBody) throws IOException, InterruptedException {
        return putWithResponse(url, jsonBody).body();
    }

    @Override
    public String put(String url) throws IOException, InterruptedException {
        return putWithResponse(url, null).body();
    }

    public Map<String, Object> commonValueParam(String rawBody) {
        return new HashMap<>() {{
            put("value", rawBody);
        }};
    }

    @Override
    public String putCommonValue(String url, String rawBody) throws IOException, InterruptedException {
        return put(url, commonValueParam(rawBody));
    }

    @Override
    public String post(String url) throws IOException, InterruptedException {
        return post(url, null);
    }

    @Override
    public String post(String url, Map<String, Object> jsonBody) throws IOException, InterruptedException {
        return postWithResponse(url, jsonBody).body();
    }

    @Override
    public String delete(String url) throws IOException, InterruptedException {
        return deleteWithResponse(url).body();
    }

    @Override
    public InputStream getFile(String url) throws IOException, InterruptedException {
        return getFileWithResponse(url).body();
    }

    @Override
    public HttpResponse<String> putFile(String url, Path localFile) throws IOException, InterruptedException {
        MimeMultipartData mimeMultipartData = MimeMultipartData.newBuilder()
                .withCharset(StandardCharsets.UTF_8)
                .addFile(localFile.getFileName().toString(), localFile, Files.probeContentType(localFile))
                .build();

        return putMultipartWithResponse(url, mimeMultipartData);
    }

    @Override
    public HttpResponse<String> putBytes(String url, String filename, String mimeType, byte[] content) throws IOException, InterruptedException {
        MimeMultipartData mimeMultipartData = MimeMultipartData.newBuilder()
                .withCharset(StandardCharsets.UTF_8)
                .addBlob(filename, filename, content, mimeType)
                .build();

        return putMultipartWithResponse(url, mimeMultipartData);
    }

    @Override
    public void addDefaultHeader(String header, String value) {
        defaultHeaders.put(header, value);
    }

    public HttpRequest.Builder requestBuilder() {
        HttpRequest.Builder builder = HttpRequest.newBuilder();
        for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        return builder;
    }


}
