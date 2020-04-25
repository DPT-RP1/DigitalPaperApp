package net.sony.dpt.network;

import net.sony.dpt.error.SonyException;
import net.sony.util.MimeMultipartData;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static net.sony.util.HttpUtils.ok;
import static net.sony.util.JsonUtils.fromJSON;

public class CheckedHttpClient extends AbstractHttpClient implements SimpleHttpClient {

    private final SimpleHttpClient uncheckedHttpClient;

    public CheckedHttpClient(final SimpleHttpClient uncheckedHttpClient) {
        this.uncheckedHttpClient = uncheckedHttpClient;
    }

    @Override
    public HttpResponse<String> getWithResponse(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = uncheckedHttpClient.getWithResponse(url);
        return this.checkString(response);
    }

    @Override
    public HttpResponse<String> putWithResponse(String url, Object serializable) throws IOException, InterruptedException {
        HttpResponse<String> response = uncheckedHttpClient.putWithResponse(url, serializable);
        return this.checkString(response);
    }

    @Override
    public HttpResponse<String> postWithResponse(String url, Map<String, Object> jsonBody) throws IOException, InterruptedException {
        HttpResponse<String> response = uncheckedHttpClient.postWithResponse(url, jsonBody);
        return this.checkString(response);
    }

    @Override
    public HttpResponse<String> deleteWithResponse(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = uncheckedHttpClient.deleteWithResponse(url);
        return this.checkString(response);
    }

    @Override
    public HttpResponse<InputStream> getFileWithResponse(String url) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = uncheckedHttpClient.getFileWithResponse(url);
        return checkStream(response);
    }

    @Override
    public HttpResponse<String> putMultipartWithResponse(String url, MimeMultipartData mimeMultipartData) throws IOException, InterruptedException {
        HttpResponse<String> response = uncheckedHttpClient.putMultipartWithResponse(url, mimeMultipartData);
        return checkString(response);
    }

    public HttpResponse<String> checkString(HttpResponse<String> response) throws IOException {
        if (!ok(response)) {
            throw fromJSON(response.body(), SonyException.class);
        }
        return response;
    }

    public HttpResponse<InputStream> checkStream(HttpResponse<InputStream> response) throws IOException {
        if (!ok(response)) {
            String bodyString = IOUtils.toString(response.body(), StandardCharsets.UTF_8);
            throw fromJSON(bodyString, SonyException.class);
        }
        return response;
    }

    @Override
    public void addDefaultHeader(String header, String value) {
        super.addDefaultHeader(header, value);
        uncheckedHttpClient.addDefaultHeader(header, value);
    }


}
