package net.sony.dpt;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sony.dpt.command.documents.EntryType;
import net.sony.util.SimpleHttpClient;
import org.apache.commons.text.StringSubstitutor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static net.sony.util.SimpleHttpClient.fromJSON;
import static net.sony.util.SimpleHttpClient.ok;

public class DigitalPaperEndpoint {

    private static final String filePathUrl = "/documents/${doc_id}/file";

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

    private static ObjectMapper objectMapper = new ObjectMapper();
    private static final String downloadRemoteIdUrl = "/documents/${remote_id}/file";
    private static final String resolveObjectByPathUrl = "/resolve/entry/path/${enc_path}";
    private static final String deleteByDocumentIdUrl = "/documents/${doc_id}";

    private static String resolve(String template, Map<String, String> variables) {
        StringSubstitutor stringSubstitutor = new StringSubstitutor(variables);
        return stringSubstitutor.replace(template);
    }

    private static Map<String, String> variable(String name, String value) {
        return new HashMap<>() {{
            put(name, value);
        }};
    }

    public URI getURI() throws URISyntaxException {
        return new URI(baseUrl);
    }

    public InputStream downloadByRemoteId(String remoteId) throws IOException, InterruptedException {
        Map<String, String> resolution = new HashMap<>() {{
            put("remote_id", remoteId);
        }};
        StringSubstitutor stringSubstitutor = new StringSubstitutor(resolution);
        String downloadUrl = stringSubstitutor.replace(downloadRemoteIdUrl);

        return simpleHttpClient.getFile(downloadUrl);
    }

    public String resolveObjectByPath(Path path) throws IOException, InterruptedException {
        String encodedPath = URLEncoder.encode(path.toString(), StandardCharsets.UTF_8);
        String url = baseUrl + resolve(resolveObjectByPathUrl, variable("enc_path", encodedPath));

        HttpResponse<String> result = simpleHttpClient.getWithResponse(url);
        if (ok(result)) {
            return (String) objectMapper.readValue(result.body(), Map.class).get("entry_id");
        }
        return null;
    }

    public void deleteByDocumentId(String remoteId) throws IOException, InterruptedException {
        simpleHttpClient.delete(resolve(deleteByDocumentIdUrl, variable("doc_id", remoteId)));
    }

    public String createDirectory(Path directory, String parentId) throws IOException, InterruptedException {
        Map<String, String> body = new HashMap<>() {{
            put("folder_name", directory.getFileName().toString());
            put("parent_folder_id", parentId);
        }};
        simpleHttpClient.post(baseUrl + "/folders2", body);
        return resolveObjectByPath(directory);
    }

    public String uploadFile(Path filePath, String parentId) throws IOException, InterruptedException {
        Map<String, String> touchParam = new HashMap<>() {{
            put("file_name", filePath.getFileName().toString());
            put("parent_folder_id", parentId);
            put("document_source", "");
        }};
        String documentId = fromJSON(simpleHttpClient.post(baseUrl + "/documents2", touchParam)).get("document_id");
        String documentUrl = baseUrl + resolve(filePathUrl, variable("doc_id", documentId));
        simpleHttpClient.putFile(documentUrl, filePath);
        return documentId;
    }

    private static final String deleteFolderUrl = "/folders/${folder_id}";
    private static final String wifiAccessPointsUrl = "/system/configs/wifi_accesspoints";
    private static final String wifiScanUrl = "/system/controls/wifi_accesspoints/scan";

    public void deleteFolderByRemoteId(String remoteId) throws IOException, InterruptedException {
        simpleHttpClient.delete(baseUrl + resolve(deleteFolderUrl, variable("folder_id", remoteId)));
    }

    public String listWifi() throws IOException, InterruptedException {
        return simpleHttpClient.get(baseUrl + wifiAccessPointsUrl);
    }

    public String scanWifi() throws IOException, InterruptedException {
        return simpleHttpClient.post(baseUrl + wifiScanUrl);
    }

    private static final String fileInfoUrl = "/documents/${file_id}";

    public void setFileInfo(String remoteId, String newParentFolderId, String newFilename) throws IOException, InterruptedException {
        Map<String, String> moveParam = new HashMap<>();
        moveParam.put("parent_folder_id", newParentFolderId);
        if (newFilename != null) {
            moveParam.put("file_name", newFilename);
        }
        simpleHttpClient.put(baseUrl + resolve(fileInfoUrl, variable("file_id", remoteId)), moveParam);
    }
}
