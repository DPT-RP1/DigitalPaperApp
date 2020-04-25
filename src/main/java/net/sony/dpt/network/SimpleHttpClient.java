package net.sony.dpt.network;

import net.sony.util.MimeMultipartData;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;

public interface SimpleHttpClient {
    String get(String url) throws IOException, InterruptedException;

    HttpResponse<String> getWithResponse(String url) throws IOException, InterruptedException;

    String put(String url, Map<String, Object> jsonBody) throws IOException, InterruptedException;

    String put(String url) throws IOException, InterruptedException;

    String putCommonValue(String url, String rawBody) throws IOException, InterruptedException;

    HttpResponse<String> putWithResponse(String url, Object serializable) throws IOException, InterruptedException;

    String post(String url) throws IOException, InterruptedException;

    String post(String url, Map<String, Object> jsonBody) throws IOException, InterruptedException;

    HttpResponse<String> postWithResponse(String url, Map<String, Object> jsonBody) throws IOException, InterruptedException;

    InputStream getFile(String url) throws IOException, InterruptedException;

    void addDefaultHeader(String header, String value);

    HttpResponse<InputStream> getFileWithResponse(String url) throws IOException, InterruptedException;

    HttpResponse<String> putMultipartWithResponse(String url, MimeMultipartData mimeMultipartData) throws IOException, InterruptedException;

    HttpResponse<String> putFile(String url, Path localFile) throws IOException, InterruptedException;

    HttpResponse<String> putBytes(String url, String filename, String mimeType, byte[] content) throws IOException, InterruptedException;

    String delete(String url) throws IOException, InterruptedException;

    HttpResponse<String> deleteWithResponse(String url) throws IOException, InterruptedException;
}
