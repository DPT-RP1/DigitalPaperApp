package net.sony.dpt.ui.cli;

import net.sony.dpt.DigitalPaperEndpoint;
import net.sony.dpt.command.authenticate.AuthenticateCommand;
import net.sony.dpt.command.authenticate.AuthenticationCookie;
import net.sony.dpt.command.device.StatusCommand;
import net.sony.dpt.command.device.TakeScreenshotCommand;
import net.sony.dpt.command.dialog.DialogCommand;
import net.sony.dpt.command.documents.DocumentListResponse;
import net.sony.dpt.command.documents.ListDocumentsCommand;
import net.sony.dpt.command.documents.TransferDocumentCommand;
import net.sony.dpt.command.firmware.FirmwareUpdatesCommand;
import net.sony.dpt.command.ping.PingCommand;
import net.sony.dpt.command.print.PrintCommand;
import net.sony.dpt.command.register.RegisterCommand;
import net.sony.dpt.command.register.RegistrationResponse;
import net.sony.dpt.command.sync.LocalSyncProgressBar;
import net.sony.dpt.command.sync.RemoteSyncProgressBar;
import net.sony.dpt.command.sync.SyncCommand;
import net.sony.dpt.command.wifi.AccessPointList;
import net.sony.dpt.command.wifi.WifiCommand;
import net.sony.dpt.persistence.DeviceInfoStore;
import net.sony.dpt.persistence.RegistrationTokenStore;
import net.sony.dpt.persistence.SyncStore;
import net.sony.dpt.ui.gui.whiteboard.Whiteboard;
import net.sony.dpt.zeroconf.FindDigitalPaper;
import net.sony.util.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.io.IOUtils;
import org.xml.sax.SAXException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class DigitalPaperCLI {

    private final Options options;
    private final CommandLineParser parser;
    private final DiffieHelman diffieHelman;
    private final CryptographyUtil cryptographyUtil;
    private final LogWriter logWriter;
    private final InputReader inputReader;
    private final RegistrationTokenStore registrationTokenStore;
    private final SyncStore syncStore;
    private final DeviceInfoStore deviceInfoStore;
    private DigitalPaperEndpoint digitalPaperEndpoint;

    public DigitalPaperCLI(DiffieHelman diffieHelman,
                           CryptographyUtil cryptographyUtil,
                           LogWriter logWriter,
                           InputReader inputReader,
                           RegistrationTokenStore registrationTokenStore,
                           SyncStore syncStore,
                           DeviceInfoStore deviceInfoStore) {

        parser = new DefaultParser();
        this.diffieHelman = diffieHelman;
        this.cryptographyUtil = cryptographyUtil;
        this.logWriter = logWriter;
        this.inputReader = inputReader;
        this.registrationTokenStore = registrationTokenStore;
        this.syncStore = syncStore;
        this.deviceInfoStore = deviceInfoStore;

        options = CommandOption.options();
    }

    private String findAddress(CommandLine commandLine) throws IOException, InterruptedException {
        String addr;
        if (commandLine.hasOption("addr")) {
            addr = commandLine.getOptionValue("addr");
        } else {
            // Before trying autoconfig, we can try loading the last ip
            String lastIp = deviceInfoStore.retrieveLastIp();
            if (lastIp != null && new PingCommand().ping(lastIp)) {
                addr = lastIp;
            } else {
                String matchSerial = null;
                if (commandLine.hasOption("serial")) {
                    matchSerial = commandLine.getOptionValue("serial");
                }
                addr = new FindDigitalPaper(logWriter, SimpleHttpClient.insecure(), matchSerial).findOneIpv4();
            }
        }
        if (addr == null || addr.isEmpty()) throw new IllegalStateException("No device found or reachable.");
        // We store the last address
        deviceInfoStore.storeLastIp(addr);

        // We test if the zeroconf digitalpaper.local is setup
        String zeroconfIp = new PingCommand().pingAndResolve(FindDigitalPaper.ZEROCONF_HOST);
        if (addr.equals(zeroconfIp)) return FindDigitalPaper.ZEROCONF_HOST;
        return addr;
    }

    private void printHelp() {
        logWriter.log(Command.printHelp());
    }

    public void execute(String[] args) throws Exception {
        CommandLine commandLine = parser.parse(options, args);

        boolean dryrun = commandLine.hasOption("dryrun");
        boolean force = commandLine.hasOption("force");



        // The arguments have to be ordered: command param1 param2 etc.
        List<String> arguments = commandLine.getArgList();
        if (arguments == null || arguments.isEmpty()) {
            printHelp();
            return;
        }
        Command command = Command.parse(args);

        if (command == Command.HELP) {
            printHelp();
            return;
        }

        String addr = findAddress(commandLine);

        if (!registrationTokenStore.registered() || command == Command.REGISTER) {
            register(SimpleHttpClient.insecure(), addr);
        }

        RegistrationResponse registrationResponse = registrationTokenStore.retrieveRegistrationToken();

        SimpleHttpClient secureHttpClient = FindDigitalPaper.ZEROCONF_HOST.equals(addr)
                ? SimpleHttpClient.secure(registrationResponse.getPemCertificate(), registrationResponse.getPrivateKey(), cryptographyUtil)
                : SimpleHttpClient.secureNoHostVerification();

        digitalPaperEndpoint = new DigitalPaperEndpoint(
                addr,
                secureHttpClient
        );

        auth(secureHttpClient);

        switch (command) {
            case LIST_DOCUMENTS:
                listDocuments();
                break;
            case DOCUMENT_INFO:
                listDocumentsInfo();
                break;
            case WIFI_LIST:
                wifiList();
                break;
            case UPLOAD:
                String localPath = arguments.get(1);
                String remotePath = null;
                if (arguments.size() > 2) {
                    remotePath = arguments.get(2);
                }
                upload(localPath, remotePath);
                break;
            case DELETE_FOLDER:
                deleteFolder(arguments.get(1));
                break;
            case DELETE:
                deleteDocument(arguments.get(1));
                break;
            case DOWNLOAD:
                String localDownloadPath = null;
                if (arguments.size() > 2) {
                    localDownloadPath = arguments.get(2);
                }
                downloadDocument(arguments.get(1), localDownloadPath);
                break;
            case NEW_FOLDER:
                newFolder(arguments.get(1));
                break;
            case MOVE:
                moveDocument(arguments.get(1), arguments.get(2));
                break;
            case COPY:
                copyDocument(arguments.get(1), arguments.get(2));
                break;
            case WIFI_SCAN:
                wifiScan();
                break;
            case SCREENSHOT:
                takeScreenshot(arguments.get(1));
                break;
            case WHITEBOARD:
                new Whiteboard(new TakeScreenshotCommand(digitalPaperEndpoint));
                break;
            case SYNC:
                sync(arguments.get(1), dryrun);
                break;
            case DIALOG:
                showDialog(arguments.get(1), arguments.get(2), arguments.get(3));
                break;
            case GET_OWNER:
                showOwner();
                break;
            case SET_OWNER:
                setOwner(arguments.get(1));
                break;
            case PING:
                ping();
                break;
            case WIFI_ADD:
                addWifi(arguments.get(1), !options.hasOption("interactive") ? arguments.get(2) : null);
                break;
            case WIFI_DEL:
                deleteWifi(arguments.get(1));
                break;
            case WIFI:
                wifiState();
                break;
            case WIFI_ENABLE:
                turnWifiOn();
                break;
            case WIFI_DISABLE:
                turnWifiOff();
                break;
            case UPDATE_FIRMWARE:
                update(force, dryrun);
                break;
            case PRINT:
                print(arguments.get(1), arguments.size() > 2 ? arguments.get(2) : null);
                break;
            case WATCH_PRINT:
                watchAndPrint(arguments.get(1));
                break;
            case BATTERY:
                showBatteryStatus();
                break;
            case STORAGE:
                showStorageStatus();
                break;
        }

    }

    private void showBatteryStatus() throws IOException, InterruptedException {
        new StatusCommand(digitalPaperEndpoint, logWriter).showBatteryStatus();
    }

    private void showStorageStatus() throws IOException, InterruptedException {
        new StatusCommand(digitalPaperEndpoint, logWriter).showStorageStatus();
    }

    private void watchAndPrint(String localFolderToWatch) throws IOException {
        PrintCommand printCommand = new PrintCommand(
                digitalPaperEndpoint,
                new DialogCommand(digitalPaperEndpoint),
                new TransferDocumentCommand(digitalPaperEndpoint),
                logWriter,
                new PingCommand(digitalPaperEndpoint, logWriter)
        );
        printCommand.watch(Path.of(localFolderToWatch));
    }

    private void print(String localPath, String remotePath) throws IOException, InterruptedException {
        PrintCommand printCommand = new PrintCommand(
                digitalPaperEndpoint,
                new DialogCommand(digitalPaperEndpoint),
                new TransferDocumentCommand(digitalPaperEndpoint),
                logWriter,
                new PingCommand(digitalPaperEndpoint, logWriter)
        );
        if (remotePath == null) {
            printCommand.print(Path.of(localPath), false);
        }
    }

    private void update(boolean force, boolean dryrun) throws IOException, InterruptedException, XPathExpressionException, SAXException, ParserConfigurationException {
        new FirmwareUpdatesCommand(
                new LocalSyncProgressBar(
                        System.out,
                        ProgressBar.ProgressStyle.RECTANGLES_1
                ),
                digitalPaperEndpoint,
                logWriter
        ).update(force, dryrun);
    }

    private void wifiState() throws IOException, InterruptedException {
        new WifiCommand(digitalPaperEndpoint, inputReader, logWriter).state();
    }

    private void addWifi(String SSID, String password) throws IOException, InterruptedException {
        WifiCommand wifiCommand = new WifiCommand(digitalPaperEndpoint, inputReader, logWriter);
        if (password == null) {
            wifiCommand.addInteractive(SSID);
        } else {
            wifiCommand.add(SSID, password);
        }
    }

    public void deleteWifi(String SSID) throws IOException, InterruptedException {
        WifiCommand wifiCommand = new WifiCommand(digitalPaperEndpoint, inputReader, logWriter);
        wifiCommand.delete(SSID);
    }

    private void copyDocument(String from, String to) throws IOException, InterruptedException {
        new TransferDocumentCommand(digitalPaperEndpoint).copy(Path.of(from), Path.of(to));
    }

    private void ping() throws IOException, URISyntaxException {
        new PingCommand(digitalPaperEndpoint, logWriter).ping();
    }

    private void showDialog(String title, String text, String buttonText) throws IOException, InterruptedException {
        new DialogCommand(digitalPaperEndpoint).show(title, text, buttonText);
    }

    private void register(SimpleHttpClient simpleHttpClient, String addr) throws IOException, BadPaddingException, InterruptedException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException, InvalidKeyException {
        RegistrationResponse registrationResponse = new RegisterCommand(addr, simpleHttpClient, diffieHelman, cryptographyUtil, logWriter, inputReader).register();
        registrationTokenStore.storeRegistrationToken(registrationResponse);
    }

    private void auth(SimpleHttpClient simpleHttpClient) throws Exception {
        RegistrationResponse registrationResponse = registrationTokenStore.retrieveRegistrationToken();
        AuthenticationCookie authenticationCookie = new AuthenticateCommand(digitalPaperEndpoint, cryptographyUtil).authenticate(registrationResponse);
        authenticationCookie.insertInCookieManager(digitalPaperEndpoint.getSecuredURI(), (CookieManager) CookieHandler.getDefault());
        authenticationCookie.insertInRequest(simpleHttpClient::addDefaultHeader);
    }

    private void listDocuments() throws IOException, InterruptedException {
        DocumentListResponse documentListResponse = new ListDocumentsCommand(digitalPaperEndpoint).listDocuments();
        documentListResponse.getEntryList().forEach(documentEntry -> logWriter.log(documentEntry.getEntryPath()));
    }

    private void listDocumentsInfo() throws IOException, InterruptedException {
        DocumentListResponse documentListResponse = new ListDocumentsCommand(digitalPaperEndpoint).listDocuments();
        documentListResponse.getEntryList().forEach(
                documentEntry -> logWriter.log(documentEntry.getEntryPath() + " - " + documentEntry)
        );
    }

    private void wifiList() throws IOException, InterruptedException {
        WifiCommand wifiCommand = new WifiCommand(digitalPaperEndpoint, inputReader, logWriter);
        AccessPointList accessPointList = wifiCommand.list();
        accessPointList.getAccessPoints().forEach(accessPoint -> logWriter.log(accessPoint.getDecodedSSID()));
    }

    private void wifiScan() throws IOException, InterruptedException {
        WifiCommand wifiCommand = new WifiCommand(digitalPaperEndpoint, inputReader, logWriter);
        AccessPointList accessPointList = wifiCommand.scan();
        accessPointList.getAccessPoints().forEach(accessPoint -> logWriter.log(accessPoint.getDecodedSSID()));
    }

    private void upload(String localPath, String remotePath) throws IOException, InterruptedException {
        if (remotePath == null) {
            remotePath = "Document/" + localPath;
        }
        new TransferDocumentCommand(digitalPaperEndpoint).upload(Path.of(localPath), Path.of(remotePath));
    }

    private void deleteFolder(String remotePath) throws IOException, InterruptedException {
        new TransferDocumentCommand(digitalPaperEndpoint).deleteFolder(Path.of(remotePath));
    }

    private void deleteDocument(String remotePath) throws IOException, InterruptedException {
        new TransferDocumentCommand(digitalPaperEndpoint).delete(Path.of(remotePath));
    }

    private void downloadDocument(String remotePath, String localPath) throws IOException, InterruptedException {
        Path localDownloadPath;
        if (localPath == null) {
            localDownloadPath = Path.of(".");
        } else {
            localDownloadPath = Path.of(localPath);
        }
        Path remoteDownloadPath = Path.of(remotePath);
        InputStream inputStream = new TransferDocumentCommand(digitalPaperEndpoint).download(remoteDownloadPath);

        if (Files.isDirectory(localDownloadPath)) {
            localDownloadPath = localDownloadPath.resolve(remoteDownloadPath.getFileName());
        }

        try (OutputStream outputStream = Files.newOutputStream(localDownloadPath)) {
            IOUtils.copy(inputStream, outputStream);
            inputStream.close();
        }
    }

    private void newFolder(String remotePath) throws IOException, InterruptedException {
        new TransferDocumentCommand(digitalPaperEndpoint).createFolderRecursively(Path.of(remotePath));
    }

    private void moveDocument(String oldPath, String newPath) throws IOException, InterruptedException {
        new TransferDocumentCommand(digitalPaperEndpoint).move(Path.of(oldPath), Path.of(newPath));
    }

    private void takeScreenshot(String target) throws IOException, InterruptedException {
        if (!target.endsWith(".png")) {
            target = target + ".png";
        }
        InputStream memoryCopy = new TakeScreenshotCommand(digitalPaperEndpoint).takeScreenshot();
        try (OutputStream targetStream = Files.newOutputStream(Path.of(target))) {
            IOUtils.copy(memoryCopy, targetStream);
            memoryCopy.close();
        }
    }

    private void sync(String localFolder, boolean dryrun) throws IOException, InterruptedException {
        new SyncCommand(
                Path.of(localFolder),
                new ListDocumentsCommand(digitalPaperEndpoint),
                new TransferDocumentCommand(digitalPaperEndpoint),
                digitalPaperEndpoint,
                logWriter,
                syncStore,
                new LocalSyncProgressBar(System.out, ProgressBar.ProgressStyle.RECTANGLES_1)
        ).sync(dryrun);
    }

    private void showOwner() throws IOException, InterruptedException {
        new StatusCommand(digitalPaperEndpoint, logWriter).showOwnerName();
    }

    private void setOwner(String ownerName) throws IOException, InterruptedException {
        new StatusCommand(digitalPaperEndpoint, logWriter).setOwnerName(ownerName);
    }

    private void turnWifiOff() throws IOException, InterruptedException {
        new WifiCommand(digitalPaperEndpoint, inputReader, logWriter).turnOff();
    }

    private void turnWifiOn() throws IOException, InterruptedException {
        new WifiCommand(digitalPaperEndpoint, inputReader, logWriter).turnOn();
    }

}
