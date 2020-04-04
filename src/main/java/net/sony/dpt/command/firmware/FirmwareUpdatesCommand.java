package net.sony.dpt.command.firmware;

import net.sony.dpt.DigitalPaperEndpoint;
import net.sony.util.LogWriter;
import net.sony.util.ProgressBar;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static net.sony.util.StringUtils.resolve;
import static net.sony.util.StringUtils.variable;

public class FirmwareUpdatesCommand {

    private static final String FIRMWARE_UPDATE_URL = "http://www.sony.net/dpt-rp1/check-for-update";
    private static final String USER_AGENT = "DigitalPaper";
    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    private static final String XPATH_FIRMWARE_FOR_MODEL = "//InformationFile/*/ApplyCondition[./Rules/Rule[@Key='Model' and @Value='${model_tag}']]";
    private static final String XPATH_FIRMWARE_VERSION = "./*/Rule[@Key='FirmwareVersion']/@Value";
    private static final String XPATH_FIRMWARE = "./*/Distribution[@ID='Firmware']";
    private final XPath xpath;
    private final ProgressBar progressBar;
    private final DigitalPaperEndpoint digitalPaperEndpoint;
    private final LogWriter logWriter;

    public FirmwareUpdatesCommand(final ProgressBar progressBar, final DigitalPaperEndpoint digitalPaperEndpoint, final LogWriter logWriter) {
        xpath = XPathFactory.newInstance().newXPath();
        this.progressBar = progressBar;
        this.digitalPaperEndpoint = digitalPaperEndpoint;
        this.logWriter = logWriter;
    }

    public String filterValidXml(String invalidSonyXml) {
        return invalidSonyXml.substring(invalidSonyXml.indexOf(XML_HEADER));
    }

    public Document parseXml(String validXml) throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new ByteArrayInputStream(validXml.getBytes(StandardCharsets.UTF_8)));
        doc.getDocumentElement().normalize();
        return doc;
    }

    public String lastestVersion(Document firmwareUpgradeDescriptor, String modelTag) throws XPathExpressionException {
        Node firmwareDefinition = (Node) xpath.evaluate(resolve(XPATH_FIRMWARE_FOR_MODEL, variable("model_tag", modelTag)), firmwareUpgradeDescriptor, XPathConstants.NODE);
        return (String) xpath.evaluate(XPATH_FIRMWARE_VERSION, firmwareDefinition, XPathConstants.STRING);
    }

    public Firmware firmware(Document firmwareUpgradeDescriptor, String modelTag) throws XPathExpressionException {
        Node firmwareDefinition = (Node) xpath.evaluate(resolve(XPATH_FIRMWARE_FOR_MODEL, variable("model_tag", modelTag)), firmwareUpgradeDescriptor, XPathConstants.NODE);
        Element firmwareInformation = (Element) xpath.evaluate(XPATH_FIRMWARE, firmwareDefinition, XPathConstants.NODE);

        Firmware firmware = new Firmware();
        firmware.version = firmwareInformation.getAttribute("Version");
        firmware.downloadUrl = firmwareInformation.getAttribute("URI");
        firmware.MAC = firmwareInformation.getAttribute("MAC");
        firmware.size = Integer.parseInt(firmwareInformation.getAttribute("Size"));

        return firmware;
    }

    public byte[] downloadAndVerify(Firmware firmware) throws IOException {
        ByteBuffer memoryBuffer = ByteBuffer.allocate(firmware.size);
        memoryBuffer.clear();

        progressBar.start();
        progressBar.progressed(0, firmware.size);
        progressBar.current("Downloading firmware v" + firmware.version);

        try (BufferedInputStream in = new BufferedInputStream(new URL(firmware.downloadUrl).openStream())) {
            byte[] readBuffer = new byte[1024 * 1024];

            int bytesRead;
            while ((bytesRead = in.read(readBuffer, 0, 1024 * 1024)) != -1) {
                memoryBuffer.put(readBuffer, 0, bytesRead);
                progressBar.progressed(memoryBuffer.position(), firmware.size);
                progressBar.progressedSize(memoryBuffer.position() / 1024 / 1024, firmware.size / 1024 / 1024);
                progressBar.repaint();
            }
        }
        byte[] result = memoryBuffer.array();
        if (result.length != firmware.size) {
            throw new IllegalStateException("The file didn't download...");
        }
        return result;
    }

    public void update(boolean force, boolean dryrun) throws IOException, InterruptedException, ParserConfigurationException, SAXException, XPathExpressionException {
        // 1. Check firmware currently installed
        FirmwareVersionResponse firmwareVersionResponse = digitalPaperEndpoint.checkVersion();

        // 2. Check version online
        HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(FIRMWARE_UPDATE_URL))
                .method("GET", HttpRequest.BodyPublishers.ofString(""))
                .header("User-Agent", USER_AGENT)
                .build();

        String malformedSonyXml = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
        String repairedXml = filterValidXml(malformedSonyXml);
        Document parsedXml = parseXml(repairedXml);
        String modelTag = firmwareVersionResponse.getModelName();

        String onlineVersion = lastestVersion(parsedXml, modelTag);

        logWriter.log("The device version is [v" + firmwareVersionResponse.getValue() + "]\nThe latest version online is [v" + onlineVersion + "]");
        if (!force && onlineVersion.equals(firmwareVersionResponse.getValue())) {
            logWriter.log("No difference, no upgrade will happen");
            return;
        }

        if (force) {
            logWriter.log("Forced mode: the update will proceed");
        }

        // 3. Download in memory if version > current
        byte[] firmwareData = downloadAndVerify(firmware(parsedXml, modelTag));

        // 4. Put on device
        digitalPaperEndpoint.putFirmwareOnDevice(firmwareData);

        // 5. Run precheck and verify


        // 6. Trigger upgrade

        // 7. Check it's done
    }

    public static class Firmware {
        public String version;
        public String downloadUrl;
        public int size;
        public String MAC;
    }
}
