package net.sony.dpt;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sony.dpt.command.documents.EntryType;
import net.sony.dpt.command.wifi.AccessPoint;
import net.sony.dpt.command.wifi.AccessPointCreationRequest;
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
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import static net.sony.util.SimpleHttpClient.fromJSON;
import static net.sony.util.SimpleHttpClient.ok;

public class DigitalPaperEndpoint {

    private static final String downloadRemoteIdUrl = "/documents/${remote_id}/file";
    private static final String resolveObjectByPathUrl = "/resolve/entry/path/${enc_path}";
    private static final String deleteByDocumentIdUrl = "/documents/${doc_id}";
    private static final String filePathUrl = "/documents/${doc_id}/file";
    private static final String deleteFolderUrl = "/folders/${folder_id}";
    private static final String wifiAccessPointsUrl = "/system/configs/wifi_accesspoints";
    private static final String wifiScanUrl = "/system/controls/wifi_accesspoints/scan";
    private static final String fileInfoUrl = "/documents/${file_id}";
    private static final String authenticateUrl = "/auth";
    private static final String takeScreenshotUrl = "/system/controls/screen_shot";
    private static final String takeFastScreenshotUrl = "/system/controls/screen_shot2?query=jpeg";

    private static final int SECURE_PORT = 8443;
    private static final int INSECURE_PORT = 8080;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String showDialogUrl = "/system/controls/indicate";
    private static final String showDialogWithUUID = "/system/controls/indicate/${indication_id}";
    private static final String ownerNameGetUrl = "/system/configs/owner";
    private static final String ownerNameSetUrl = "/system/configs/owner";
    private static final String copyUrl = "/documents/${document_id}/copy";
    private static final String wifiRegister = "/system/controls/wifi_accesspoints/register";
    private static final String wifiRemoveUrl = "/system/configs/wifi_accesspoints/${ssid}/${security}";
    private final SimpleHttpClient simpleHttpClient;
    private final String secureBaseUrl;
    private final String insecureBaseUrl;
    private final String ip;

    // TODO: might be smarter to even detect the cert host, but it should always be this one
    private static final String ZEROCONF_HOST = "digitalpaper.local";

    public DigitalPaperEndpoint(String ip, SimpleHttpClient simpleHttpClient) throws KeyManagementException, NoSuchAlgorithmException {
        this.ip = ip;
        this.secureBaseUrl = "https://" + ip + ":" + SECURE_PORT;
        this.insecureBaseUrl = "http://" + ip + ":" + INSECURE_PORT;
        this.simpleHttpClient = simpleHttpClient;
    }

    private static String resolve(String template, Map<String, String>... variables) {
        Map<String, String> finalMap = new HashMap<>();
        for (Map<String, String> variable : variables) {
            finalMap.putAll(variable);
        }
        StringSubstitutor stringSubstitutor = new StringSubstitutor(finalMap);
        return stringSubstitutor.replace(template);
    }

    private static Map<String, String> variable(String name, String value) {
        return new HashMap<>() {{
            put(name, value);
        }};
    }

    public String getNonce(String clientId) throws IOException, InterruptedException {
        return (String) fromJSON(simpleHttpClient.get(secureBaseUrl + "/auth/nonce/" + clientId)).get("nonce");
    }

    public String listDocuments() throws IOException, InterruptedException {
        return simpleHttpClient.get(secureBaseUrl + "/documents2");
    }

    public String listDocuments(EntryType entryType) throws IOException, InterruptedException {
        return simpleHttpClient.get(secureBaseUrl + "/documents2?entry_type=" + entryType);
    }

    public URI getURI() throws URISyntaxException {
        return new URI(secureBaseUrl);
    }

    public InputStream downloadByRemoteId(String remoteId) throws IOException, InterruptedException {
        Map<String, String> resolution = new HashMap<>() {{
            put("remote_id", remoteId);
        }};
        StringSubstitutor stringSubstitutor = new StringSubstitutor(resolution);
        String downloadUrl = stringSubstitutor.replace(downloadRemoteIdUrl);

        return simpleHttpClient.getFile(secureBaseUrl + downloadUrl);
    }

    public String resolveObjectByPath(Path path) throws IOException, InterruptedException {
        String encodedPath = URLEncoder.encode(path.toString(), StandardCharsets.UTF_8);
        String url = secureBaseUrl + resolve(resolveObjectByPathUrl, variable("enc_path", encodedPath));

        HttpResponse<String> result = simpleHttpClient.getWithResponse(url);
        if (ok(result)) {
            return (String) objectMapper.readValue(result.body(), Map.class).get("entry_id");
        }
        return null;
    }

    public void deleteByDocumentId(String remoteId) throws IOException, InterruptedException {
        simpleHttpClient.delete(secureBaseUrl + resolve(deleteByDocumentIdUrl, variable("doc_id", remoteId)));
    }

    public void deleteFolderByRemoteId(String remoteId) throws IOException, InterruptedException {
        simpleHttpClient.delete(secureBaseUrl + resolve(deleteFolderUrl, variable("folder_id", remoteId)));
    }

    public String listWifi() throws IOException, InterruptedException {
        return simpleHttpClient.get(secureBaseUrl + wifiAccessPointsUrl);
    }

    public String scanWifi() throws IOException, InterruptedException {
        return simpleHttpClient.post(secureBaseUrl + wifiScanUrl);
    }

    private static final String WIFI_STATE_URL = "/system/status/wifi_state";

    public AccessPoint wifiState() throws IOException, InterruptedException {
        return objectMapper.readValue(simpleHttpClient.get(secured(WIFI_STATE_URL)), AccessPoint.class);
    }

    public InputStream takeScreenshot() throws IOException, InterruptedException {
        return simpleHttpClient.getFile(secured(takeScreenshotUrl));
    }

    public InputStream takeFastScreenshot() throws IOException, InterruptedException {
        return simpleHttpClient.getFile(secured(takeFastScreenshotUrl));
    }

    public String createDirectory(Path directory, String parentId) throws IOException, InterruptedException {
        Map<String, Object> body = new HashMap<>() {{
            put("folder_name", directory.getFileName().toString());
            put("parent_folder_id", parentId);
        }};
        simpleHttpClient.post(secureBaseUrl + "/folders2", body);
        return resolveObjectByPath(directory);
    }

    public String uploadFile(Path filePath, String parentId) throws IOException, InterruptedException {
        Map<String, Object> touchParam = new HashMap<>() {{
            put("file_name", filePath.getFileName().toString());
            put("parent_folder_id", parentId);
            put("document_source", "");
        }};
        String documentId = (String) fromJSON(simpleHttpClient.post(secureBaseUrl + "/documents2", touchParam)).get("document_id");
        String documentUrl = secureBaseUrl + resolve(filePathUrl, variable("doc_id", documentId));
        simpleHttpClient.putFile(documentUrl, filePath);
        return documentId;
    }

    public void setFileInfo(String remoteId, String newParentFolderId, String newFilename) throws IOException, InterruptedException {
        Map<String, Object> moveParam = new HashMap<>();
        moveParam.put("parent_folder_id", newParentFolderId);
        if (newFilename != null) {
            moveParam.put("file_name", newFilename);
        }
        simpleHttpClient.put(secureBaseUrl + resolve(fileInfoUrl, variable("file_id", remoteId)), moveParam);
    }

    public String authenticate(Map<String, Object> params) throws IOException, InterruptedException {
        HttpResponse<String> response = simpleHttpClient.putWithResponse(secureBaseUrl + authenticateUrl, params);
        return response.headers().map().get("set-cookie").get(0).split("; ")[0].split("=")[1];
    }

    public void showDialog(String title, String text, String buttonText, boolean animate) throws IOException, InterruptedException {
        Map<String, Object> params = new HashMap<>() {{
            put("dialog_params", new HashMap<>() {{
                put("title", title);
                put("message", text);
                put("button_caption", buttonText);
            }});
        }};
        simpleHttpClient.post(secureBaseUrl + showDialogUrl, params);
    }

    public void showDialog(String UUID, String title, String text, String buttonText, boolean animate) throws IOException, InterruptedException {
        Map<String, Object> params = new HashMap<>() {{
            put("dialog_params", new HashMap<>() {{
                put("title", title);
                put("message", text);
                put("button_caption", buttonText);
            }});
            put("show_animation", animate);
        }};
        simpleHttpClient.put(secureBaseUrl + resolve(showDialogWithUUID, variable("indication_id", UUID)), params);
    }

    public String getOwnerName() throws IOException, InterruptedException {
        return (String) fromJSON(simpleHttpClient.get(secureBaseUrl + ownerNameGetUrl)).get("value");
    }

    public void setOwnerName(String escapedName) throws IOException, InterruptedException {
        simpleHttpClient.putCommonValue(secureBaseUrl + ownerNameSetUrl, escapedName);
    }

    public void copy(String from, String toFolder, String toFilename) throws IOException, InterruptedException {
        simpleHttpClient.post(secured(resolve(copyUrl, variable("document_id", from))), new HashMap<>() {{
            put("parent_folder_id", toFolder);
            if (toFilename != null) put("file_name", toFilename);
        }});
    }

    public String secured(String path) {
        return secureBaseUrl + path;
    }

    public void addWifi(AccessPointCreationRequest found) throws IOException, InterruptedException {
        simpleHttpClient.putWithResponse(secured(wifiRegister), found);
    }

    public void removeWifi(AccessPoint found) throws IOException, InterruptedException {
        simpleHttpClient.delete(
            secured(
                resolve(
                    wifiRemoveUrl,
                    variable("ssid", found.getDecodedSSID()),
                    variable("security", found.getSecurity())
                )
            )
        );
    }
}
