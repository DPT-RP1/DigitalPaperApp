package net.sony.dpt.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sony.dpt.command.documents.EntryType;
import net.sony.dpt.command.firmware.FirmwareVersionResponse;
import net.sony.dpt.command.wifi.AccessPoint;
import net.sony.dpt.command.wifi.AccessPointCreationRequest;
import net.sony.dpt.error.SonyException;
import org.apache.commons.text.StringSubstitutor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static net.sony.util.HttpUtils.ok;
import static net.sony.util.JsonUtils.fromJSON;
import static net.sony.util.StringUtils.resolve;
import static net.sony.util.StringUtils.variable;

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
    private static final String showDialogUrl = "/system/controls/indicate";
    private static final String DIALOG_WITH_ID_URL = "/system/controls/indicate/${indication_id}";
    private static final String ownerNameGetUrl = "/system/configs/owner";
    private static final String ownerNameSetUrl = "/system/configs/owner";
    private static final String copyUrl = "/documents/${document_id}/copy";
    private static final String wifiRegister = "/system/controls/wifi_accesspoints/register";
    private static final String wifiRemoveUrl = "/system/configs/wifi_accesspoints/${ssid}/${security}";

    private static final String FIRMWARE_VERSION_URL = "/system/status/firmware_version";
    private static final String FIRMWARE_PUT_ON_DEVICE_URL = "/system/controls/update_firmware/file";

    private static final int SECURE_PORT = 8443;
    private static final int INSECURE_PORT = 8080;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String WIFI_ON_OFF_URL = "/system/configs/wifi";
    private static final String WIFI_STATE_URL = "/system/status/wifi_state";

    private final SimpleHttpClient simpleHttpClient;
    private final String secureBaseUrl;
    private final String insecureBaseUrl;
    private final String addr;

    public DigitalPaperEndpoint(String addr, SimpleHttpClient simpleHttpClient) {
        this.addr = addr;
        this.secureBaseUrl = "https://" + addr + ":" + SECURE_PORT;
        this.insecureBaseUrl = "http://" + addr + ":" + INSECURE_PORT;
        this.simpleHttpClient = simpleHttpClient;
    }

    private static final String DOCUMENT_INFO_URL = "/documents2/${document_id}";
    public String documentInfos(String objectId) throws IOException, InterruptedException {
        return simpleHttpClient.get(secured(resolve(DOCUMENT_INFO_URL, variable("document_id", objectId))));
    }

    public String getNonce(String clientId) throws IOException, InterruptedException {
        return (String) fromJSON(simpleHttpClient.get(secured("/auth/nonce/" + clientId))).get("nonce");
    }

    public String listDocuments() throws IOException, InterruptedException {
        return simpleHttpClient.get(secured("/documents2"));
    }

    public String listDocuments(EntryType entryType) throws IOException, InterruptedException {
        return simpleHttpClient.get(secured("/documents2?entry_type=" + entryType));
    }

    public URI getSecuredURI() throws URISyntaxException {
        return new URI(secureBaseUrl);
    }

    public URI getInsecureURI() throws URISyntaxException {
        return new URI(insecureBaseUrl);
    }

    public String getAddr() {
        return addr;
    }

    public InputStream downloadByRemoteId(String remoteId) throws IOException, InterruptedException {
        Map<String, String> resolution = new HashMap<>() {{
            put("remote_id", remoteId);
        }};
        StringSubstitutor stringSubstitutor = new StringSubstitutor(resolution);
        String downloadUrl = stringSubstitutor.replace(downloadRemoteIdUrl);

        return simpleHttpClient.getFile(secured(downloadUrl));
    }

    public String resolveObjectByPath(Path path) throws IOException, InterruptedException {
        String encodedPath = URLEncoder.encode(path.toString(), StandardCharsets.UTF_8);
        String url = secured(resolve(resolveObjectByPathUrl, variable("enc_path", encodedPath)));

        try {
            HttpResponse<String> result = simpleHttpClient.getWithResponse(url);
            if (ok(result)) {
                return (String) objectMapper.readValue(result.body(), Map.class).get("entry_id");
            }
        } catch (SonyException e) {
            // A sony error code is send on file not found
            if (e.getCodeParsed() != SonyException.ErrorCode.RESOURCE_NOT_FOUND) throw e;
        }

        return null;
    }

    public void deleteByDocumentId(String remoteId) throws IOException, InterruptedException {
        simpleHttpClient.delete(secured(resolve(deleteByDocumentIdUrl, variable("doc_id", remoteId))));
    }

    public void deleteFolderByRemoteId(String remoteId) throws IOException, InterruptedException {
        simpleHttpClient.delete(secured(resolve(deleteFolderUrl, variable("folder_id", remoteId))));
    }

    public String listWifi() throws IOException, InterruptedException {
        return simpleHttpClient.get(secured(wifiAccessPointsUrl));
    }

    public String scanWifi() throws IOException, InterruptedException {
        return simpleHttpClient.post(secured(wifiScanUrl));
    }

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
        simpleHttpClient.post(secured("/folders2"), body);
        return resolveObjectByPath(directory);
    }

    public String touchFile(String filename, String parentId) throws IOException, InterruptedException {
        Map<String, Object> touchParam = new HashMap<>() {{
            put("file_name", filename);
            put("parent_folder_id", parentId);
            put("document_source", "");
        }};
        return (String) fromJSON(simpleHttpClient.post(secured("/documents2"), touchParam)).get("document_id");
    }

    public void uploadDocumentData(String documentId, String filename, byte[] content) throws IOException, InterruptedException {
        String documentUrl = secured(resolve(filePathUrl, variable("doc_id", documentId)));
        simpleHttpClient.putBytes(documentUrl, filename, "application/pdf", content);
    }

    public String uploadFile(String filename, byte[] content, String parentId) throws IOException, InterruptedException {
        String documentId = touchFile(filename, parentId);
        uploadDocumentData(documentId, filename, content);
        return documentId;
    }

    public String uploadFile(Path filePath, String parentId) throws IOException, InterruptedException {
        Map<String, Object> touchParam = new HashMap<>() {{
            put("file_name", filePath.getFileName().toString());
            put("parent_folder_id", parentId);
            put("document_source", "");
        }};
        String documentId = (String) fromJSON(simpleHttpClient.post(secured("/documents2"), touchParam)).get("document_id");
        String documentUrl = secured(resolve(filePathUrl, variable("doc_id", documentId)));
        simpleHttpClient.putFile(documentUrl, filePath);
        return documentId;
    }

    public void setFileInfo(String remoteId, String newParentFolderId, String newFilename) throws IOException, InterruptedException {
        Map<String, Object> moveParam = new HashMap<>();
        moveParam.put("parent_folder_id", newParentFolderId);
        if (newFilename != null) {
            moveParam.put("file_name", newFilename);
        }
        simpleHttpClient.put(secured(resolve(fileInfoUrl, variable("file_id", remoteId))), moveParam);
    }

    public String authenticate(Map<String, Object> params) throws IOException, InterruptedException {
        HttpResponse<String> response = simpleHttpClient.putWithResponse(secured(authenticateUrl), params);
        return response.headers().map().get("set-cookie").get(0).split("; ")[0].split("=")[1];
    }

    public void showDialog(String title, String text, String buttonText) throws IOException, InterruptedException {
        Map<String, Object> params = new HashMap<>() {{
            put("dialog_params", new HashMap<>() {{
                put("title", title);
                put("message", text);
                put("button_caption", buttonText);
            }});
        }};
        simpleHttpClient.post(secured(showDialogUrl), params);
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
        simpleHttpClient.put(secured(resolve(DIALOG_WITH_ID_URL, variable("indication_id", UUID))), params);
    }

    public String getOwnerName() throws IOException, InterruptedException {
        return (String) fromJSON(simpleHttpClient.get(secured(ownerNameGetUrl))).get("value");
    }

    public void setOwnerName(String escapedName) throws IOException, InterruptedException {
        simpleHttpClient.putCommonValue(secured(ownerNameSetUrl), escapedName);
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

    public void setWifiState(boolean enabled) throws IOException, InterruptedException {
        try {
            simpleHttpClient.put(secured(WIFI_ON_OFF_URL), new HashMap<>() {{
                put("value", enabled ? "on" : "off");
            }});
        } catch (HttpTimeoutException ignored) {
        }
    }

    public FirmwareVersionResponse checkVersion() throws IOException, InterruptedException {
        return fromJSON(simpleHttpClient.get(secured(FIRMWARE_VERSION_URL)), FirmwareVersionResponse.class);
    }

    public void putFirmwareOnDevice(byte[] firmware) throws IOException, InterruptedException {
        simpleHttpClient.putBytes(secured(FIRMWARE_PUT_ON_DEVICE_URL), "FwUpdater.pkg", "application/x-newton-compatible-pkg", firmware);
    }

    public void hideDialog(String dialogId) throws IOException, InterruptedException {
        simpleHttpClient.delete(secured(resolve(DIALOG_WITH_ID_URL, variable("indication_id", dialogId))));
    }

    private static final String OPEN_DOCUMENT_URL = "/viewer/controls/open2";
    public void openDocument(String documentId) throws IOException, InterruptedException {
        simpleHttpClient.put(secured(OPEN_DOCUMENT_URL), new HashMap<>() {{
            put("document_id", documentId);
        }});
    }

    private static final String BATTERY_STATUS_URL = "/system/status/battery";
    public String getBatteryStatus() throws IOException, InterruptedException {
        return simpleHttpClient.get(secured(BATTERY_STATUS_URL));
    }

    private static final String STORAGE_STATUS_URL = "/system/status/storage";
    public String getStorageStatus() throws IOException, InterruptedException {
        return simpleHttpClient.get(secured(STORAGE_STATUS_URL));
    }

    public String rawSecuredGet(String url) throws IOException, InterruptedException {
        return simpleHttpClient.get(secured(url));
    }

    private static final String FIRMWARE_PRECHECK_URL = "/system/controls/update_firmware/precheck";
    public String precheckUpdate() throws IOException, InterruptedException {
        return simpleHttpClient.get(secured(FIRMWARE_PRECHECK_URL));
    }

    private static final String FIRMWARRE_TRIGGER_URL = "/system/controls/update_firmware";
    public void triggerUpdate() throws IOException, InterruptedException {
        simpleHttpClient.put(secured(FIRMWARRE_TRIGGER_URL));
    }

    private static final String FOLDER_CONTENT_URL = "/folders/${folder_id}/entries2";
    public String getFolderContent(String folderId) throws IOException, InterruptedException {
        return simpleHttpClient.get(secured(resolve(FOLDER_CONTENT_URL, variable("folder_id", folderId))));
    }

    private static final String UPDATE_FOLDER_URL = "/folders2/${folder_id}";
    public void updateFolder(String folderId, String newParentId, String newName) throws IOException, InterruptedException {
        simpleHttpClient.put(secured(resolve(UPDATE_FOLDER_URL, variable("folder_id", folderId))), new HashMap<>() {{
            if (newParentId != null) put("parent_folder_id", newParentId);
            if (newName != null) put("folder_name", newName);
        }});
    }

    private static final String NOTE_TEMPLATE = "/viewer/configs/note_templates";
    public String createNoteTemplate(String name) throws IOException, InterruptedException {
        return (String) fromJSON(simpleHttpClient.post(secured(NOTE_TEMPLATE), new HashMap<>() {{
            put("template_name", name);
        }})).get("note_template_id");
    }

    private static final String NOTE_TEMPLATE_FILE = "/viewer/configs/note_templates/${note_template_id}/file";
    public void insertNoteTemplateFile(String noteId, Path sourcePath) throws IOException, InterruptedException {
        simpleHttpClient.putFile(
            secured(resolve(NOTE_TEMPLATE_FILE, variable("note_template_id", noteId))),
            sourcePath
        );
    }

    private static final String CONFIG_URL = "/system/configs";
    public String getSystemConfigs() throws IOException, InterruptedException {
        return simpleHttpClient.get(secured(CONFIG_URL));
    }

    private static final String SET_CONFIG_URL = "/system/configs/${key}";
    public void setSystemConfig(String key, Map<String, Object> value) throws IOException, InterruptedException {
        simpleHttpClient.put(secured(resolve(SET_CONFIG_URL, variable("key", key))), value);
    }
}
