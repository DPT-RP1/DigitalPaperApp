package net.sony.dpt.ui.cli;

import com.android.ddmlib.*;
import net.sony.dpt.command.authenticate.AuthenticateCommand;
import net.sony.dpt.command.authenticate.AuthenticationCookie;
import net.sony.dpt.command.device.SystemConfigCommand;
import net.sony.dpt.command.device.TakeScreenshotCommand;
import net.sony.dpt.command.dialog.DialogCommand;
import net.sony.dpt.command.documents.DocumentCommand;
import net.sony.dpt.command.documents.DocumentListResponse;
import net.sony.dpt.command.firmware.FirmwareUpdatesCommand;
import net.sony.dpt.command.firmware.RootCommand;
import net.sony.dpt.command.notes.NoteTemplateCommand;
import net.sony.dpt.command.ping.PingCommand;
import net.sony.dpt.command.print.PrintCommand;
import net.sony.dpt.command.register.RegisterCommand;
import net.sony.dpt.command.register.RegistrationResponse;
import net.sony.dpt.command.reversing.ReverseEngineeringCommand;
import net.sony.dpt.command.root.AdbCommand;
import net.sony.dpt.command.root.DiagnosticCommand;
import net.sony.dpt.command.root.FirmwareCommand;
import net.sony.dpt.command.sync.LocalSyncProgressBar;
import net.sony.dpt.command.sync.SyncCommand;
import net.sony.dpt.command.wifi.AccessPointList;
import net.sony.dpt.command.wifi.WifiCommand;
import net.sony.dpt.fuse.DptFuseMounter;
import net.sony.dpt.network.CheckedHttpClient;
import net.sony.dpt.network.DigitalPaperEndpoint;
import net.sony.dpt.network.SimpleHttpClient;
import net.sony.dpt.network.UncheckedHttpClient;
import net.sony.dpt.persistence.DeviceInfoStore;
import net.sony.dpt.persistence.LastCommandRunStore;
import net.sony.dpt.persistence.RegistrationTokenStore;
import net.sony.dpt.persistence.SyncStore;
import net.sony.dpt.root.DiagnosticManager;
import net.sony.dpt.root.FirmwarePacker;
import net.sony.dpt.root.RootPacker;
import net.sony.dpt.ui.gui.whiteboard.Orientation;
import net.sony.dpt.ui.gui.whiteboard.Whiteboard;
import net.sony.dpt.ui.html.WhiteboardBackend;
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
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.List;

public class DigitalPaperCLI {

    private final Options options;
    private final CommandLineParser parser;
    private final DiffieHelman diffieHelman;
    private final CryptographyUtils cryptographyUtils;
    private final LogWriter logWriter;
    private final InputReader inputReader;
    private final RegistrationTokenStore registrationTokenStore;
    private final SyncStore syncStore;
    private final DeviceInfoStore deviceInfoStore;
    private final LastCommandRunStore lastCommandRunStore;
    private FindDigitalPaper findDigitalPaper;

    private DigitalPaperEndpoint digitalPaperEndpoint;

    public DigitalPaperCLI(DiffieHelman diffieHelman,
                           CryptographyUtils cryptographyUtils,
                           LogWriter logWriter,
                           InputReader inputReader,
                           RegistrationTokenStore registrationTokenStore,
                           SyncStore syncStore,
                           DeviceInfoStore deviceInfoStore,
                           LastCommandRunStore lastCommandRunStore) {

        parser = new DefaultParser();
        this.diffieHelman = diffieHelman;
        this.cryptographyUtils = cryptographyUtils;
        this.logWriter = logWriter;
        this.inputReader = inputReader;
        this.registrationTokenStore = registrationTokenStore;
        this.syncStore = syncStore;
        this.deviceInfoStore = deviceInfoStore;
        this.lastCommandRunStore = lastCommandRunStore;

        options = CommandOption.options();
    }

    private void printHelp() {
        logWriter.log(Command.printHelp());
    }

    public void execute(String[] args) throws Exception {
        CommandLine commandLine = parser.parse(options, args);

        boolean dryrun = commandLine.hasOption("dryrun");
        boolean force = commandLine.hasOption("force");
        boolean hasAddr = commandLine.hasOption("addr");
        boolean matchSerial = commandLine.hasOption("serial");

        Command command = Command.parse(args);

        if (commandLine.hasOption("version")) {
            printVersion();
            return;
        }

        // The arguments have to be ordered: command param1 param2 etc.
        List<String> arguments = commandLine.getArgList();
        if (arguments == null || arguments.isEmpty()) {
            printHelp();
            return;
        }

        findDigitalPaper = new FindDigitalPaper(
                logWriter,
                deviceInfoStore,
                new CheckedHttpClient(UncheckedHttpClient.insecure()),
                matchSerial ? commandLine.getOptionValue("serial") : null,
                hasAddr ? commandLine.getOptionValue("addr") : null);

        // This are pre-registration command
        switch (command) {
            case HELP:
                printHelp();
                return;
            case DIAG_FETCH:
                diagFetch(arguments.get(2), arguments.get(3));
                return;
            case DIAG_EXIT:
                diagExit();
                return;
            case UNPACK:
                unpack(arguments.get(1), arguments.get(2));
                return;
            case ADB_LIST_EXTENSIONS:
                adbListExtensions();
                return;
            case ADB_FETCH_EXTENSION:
                adbFetchExtension(arguments.get(2), arguments.get(3));
                return;
            case ADB_SETUP_EXTENSION:
                adbSetupExtension(arguments.get(2), arguments.get(3), arguments.get(4), arguments.get(5));
                return;
            case ADB_REMOVE_EXTENSION:
                adbRemoveExtension(arguments.get(2));
                return;
            case ADB_INSTALL_APK:
                adbInstallApk(arguments.get(2));
                return;
        }

        String addr = findDigitalPaper.findAddress();

        if (!registrationTokenStore.registered() || command == Command.REGISTER) {
            register(new CheckedHttpClient(UncheckedHttpClient.insecure()), addr);
            return;
        }

        RegistrationResponse registrationResponse = registrationTokenStore.retrieveRegistrationToken();

        SimpleHttpClient secureHttpClient = FindDigitalPaper.ZEROCONF_HOST.equals(addr)
                ? new CheckedHttpClient(UncheckedHttpClient.secure(registrationResponse.getPemCertificate(), registrationResponse.getPrivateKey(), cryptographyUtils))
                : new CheckedHttpClient(UncheckedHttpClient.secureNoHostVerification());

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
                showWhiteboard(
                        commandLine.hasOption("orientation") ? commandLine.getOptionValue("orientation") : null,
                        commandLine.hasOption("scalingFactor") ? commandLine.getOptionValue("scalingFactor") : null
                );

                break;
            case SYNC:
                sync(lastCommandRunStore.retrieveOneArgument(command, arguments), dryrun);
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
            case CHECK_FIRMWARE:
                checkFirmware();
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
            case RAW_GET:
                rawGet(arguments.get(1));
                break;
            case WHITEBOARD_HTML:
                whiteboardHtml(commandLine.hasOption("orientation") ? commandLine.getOptionValue("orientation") : null);
                break;
            case MOUNT:
                mount(lastCommandRunStore.retrieveOneArgument(command, arguments));
                break;
            case INSERT_NOTE_TEMPLATE:
                insertNoteTemplate(arguments.get(1), arguments.get(2));
                break;
            case GET_CONFIGURATION:
                getConfiguration(arguments.size() < 2 ? "./config.json" : arguments.get(1));
                break;
            case SET_CONFIGURATION:
                setConfiguration(arguments.size() < 2 ? "./config.json" : arguments.get(1));
                break;
            case ROOT:
                root(dryrun);
                break;
        }

    }

    private void showWhiteboard(String orientationOption, String scalingFactorOption) throws IOException, InterruptedException {
        Orientation orientation = Orientation.LANDSCAPE;
        float scalingFactor = 0.5f;
        if (orientationOption != null) orientation = Orientation.valueOf(orientationOption.toUpperCase());
        if (scalingFactorOption != null) scalingFactor = Float.parseFloat(scalingFactorOption);
        new Whiteboard(new TakeScreenshotCommand(digitalPaperEndpoint), orientation, scalingFactor);
    }

    private void adbInstallApk(String localPath) throws InterruptedException, IOException, URISyntaxException, ParserConfigurationException, InstallException, SyncException, SAXException, TimeoutException, AdbCommandRejectedException, XPathExpressionException, ShellCommandUnresponsiveException {
        AdbCommand adbCommand = null;
        try {
            adbCommand = new AdbCommand(logWriter,
                    new LocalSyncProgressBar(
                        System.out,
                        ProgressBar.ProgressStyle.SQUARES_1
                    ),
                    findDigitalPaper);
            adbCommand.installApk(Path.of(localPath));
        } finally {
            if (adbCommand != null) { adbCommand.tearDown(); }
        }
    }

    private void adbRemoveExtension(String name) throws InterruptedException, TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException, SyncException {
        AdbCommand adbCommand = null;
        try {
            adbCommand = new AdbCommand(logWriter,
                    new LocalSyncProgressBar(
                        System.out,
                        ProgressBar.ProgressStyle.SQUARES_1
                    ),
                    findDigitalPaper
            );
            adbCommand.removeExtension(name);
        } finally {
            if (adbCommand != null) { adbCommand.tearDown(); }
        }
    }

    private void adbSetupExtension(String name, String component, String action, String icon) throws InterruptedException, IOException, SyncException, TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException {
        AdbCommand adbCommand = null;
        try {
            adbCommand = new AdbCommand(
                    logWriter,
                    new LocalSyncProgressBar(
                        System.out,
                        ProgressBar.ProgressStyle.SQUARES_1
                    ),
                    findDigitalPaper
            );
            adbCommand.setupExtension(name, component, action, Files.readAllBytes(Path.of(icon)));
        } finally {
            if (adbCommand != null) { adbCommand.tearDown(); }
        }
    }

    private void adbListExtensions() throws InterruptedException, IOException {
        AdbCommand adbCommand = null;
        try {
            adbCommand = new AdbCommand(logWriter, null, findDigitalPaper);
            adbCommand.showExtensionsDescriptors();
        } finally {
            if (adbCommand != null) { adbCommand.tearDown(); }
        }
    }

    private void adbFetchExtension(final String name, final String targetFolder) throws InterruptedException, TimeoutException, AdbCommandRejectedException, SyncException, IOException {
        AdbCommand adbCommand = null;
        try {
            adbCommand = new AdbCommand(
                    logWriter,
                    new LocalSyncProgressBar(System.out, ProgressBar.ProgressStyle.SQUARES_1),
                    findDigitalPaper
            );
            adbCommand.downloadExtensionsDescriptor(name, Path.of(targetFolder));
        } finally {
            if (adbCommand != null) { adbCommand.tearDown(); }
        }

    }

    private void unpack(final String pkgFile, final String targetDirectory) throws NoSuchPaddingException, SignatureException, NoSuchAlgorithmException, IOException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException, InvalidKeyException {
        new FirmwareCommand(logWriter, new FirmwarePacker(cryptographyUtils, logWriter))
                .unpack(Path.of(pkgFile), Path.of(targetDirectory));
    }

    private void diagExit() throws IOException, InterruptedException {
        new DiagnosticCommand(
                new DiagnosticManager(
                        logWriter,
                        inputReader
                )
        ).exitDiagnosticMode();
    }

    private void diagFetch(String remotePath, String localPath) throws IOException, InterruptedException {
        new DiagnosticCommand(
            new DiagnosticManager(
                logWriter,
                inputReader
            )
        ).fetchFile(Path.of(remotePath), Path.of(localPath));
    }

    private void root(boolean dryrun) throws InterruptedException, IOException, URISyntaxException {
        new RootCommand(logWriter,
                new FirmwareUpdatesCommand(
                        new LocalSyncProgressBar(System.out, ProgressBar.ProgressStyle.SQUARES_1),
                        digitalPaperEndpoint,
                        logWriter,
                        inputReader
                ),
                new RootPacker()).rootDevice(dryrun);
    }

    private void getConfiguration(String path) throws IOException, InterruptedException {
        new SystemConfigCommand(digitalPaperEndpoint, logWriter).saveSystemConfigsToLocal(Path.of(path));
    }

    private void setConfiguration(String path) throws IOException, InterruptedException {
        new SystemConfigCommand(digitalPaperEndpoint, logWriter).sendSystemConfigsToRemote(Path.of(path));
    }

    private void insertNoteTemplate(String name, String path) throws IOException, InterruptedException {
        new NoteTemplateCommand(digitalPaperEndpoint).insertNoteTemplate(name, Path.of(path));
    }

    private void mount(String mountPoint) throws IOException, InterruptedException {
        new DptFuseMounter(new DocumentCommand(digitalPaperEndpoint), logWriter).mountDpt(Path.of(mountPoint));
    }

    private void printVersion() {
        logWriter.log(Command.printVersion());
    }

    private void checkFirmware() throws InterruptedException, ParserConfigurationException, SAXException, XPathExpressionException, IOException {
        new FirmwareUpdatesCommand(null, digitalPaperEndpoint, logWriter, null).checkForUpdates();
    }

    private void whiteboardHtml(String orientationOption) throws IOException, URISyntaxException {
        Orientation orientation = Orientation.LANDSCAPE;
        if (orientationOption != null) orientation = Orientation.valueOf(orientationOption.toUpperCase());

        WhiteboardBackend whiteboardBackend = new WhiteboardBackend(
                new TakeScreenshotCommand(digitalPaperEndpoint),
                logWriter,
                orientation
        );
        int port = whiteboardBackend.bind();
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(new URI("http://localhost:" + port + "/frontend"));
        }
    }

    private void rawGet(String url) throws IOException, InterruptedException {
        new ReverseEngineeringCommand(digitalPaperEndpoint, logWriter).sendGet(url);
    }

    private void showBatteryStatus() throws IOException, InterruptedException {
        new SystemConfigCommand(digitalPaperEndpoint, logWriter).showBatteryStatus();
    }

    private void showStorageStatus() throws IOException, InterruptedException {
        new SystemConfigCommand(digitalPaperEndpoint, logWriter).showStorageStatus();
    }

    private void watchAndPrint(String localFolderToWatch) throws IOException {
        PrintCommand printCommand = new PrintCommand(
                digitalPaperEndpoint,
                new DialogCommand(digitalPaperEndpoint),
                new DocumentCommand(digitalPaperEndpoint),
                logWriter,
                new PingCommand(digitalPaperEndpoint, logWriter)
        );
        printCommand.watch(Path.of(localFolderToWatch));
    }

    private void print(String localPath, String remotePath) throws IOException, InterruptedException {
        PrintCommand printCommand = new PrintCommand(
                digitalPaperEndpoint,
                new DialogCommand(digitalPaperEndpoint),
                new DocumentCommand(digitalPaperEndpoint),
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
                logWriter,
                inputReader
        ).updateOfficial(force, dryrun);
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
        new DocumentCommand(digitalPaperEndpoint).copy(Path.of(from), Path.of(to));
    }

    private void ping() throws IOException, URISyntaxException {
        new PingCommand(digitalPaperEndpoint, logWriter).ping();
    }

    private void showDialog(String title, String text, String buttonText) throws IOException, InterruptedException {
        new DialogCommand(digitalPaperEndpoint).show(title, text, buttonText);
    }

    private void register(SimpleHttpClient simpleHttpClient, String addr) throws IOException, BadPaddingException, InterruptedException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException, InvalidKeyException {
        RegistrationResponse registrationResponse = new RegisterCommand(addr, simpleHttpClient, diffieHelman, cryptographyUtils, logWriter, inputReader)
                .register();
        registrationTokenStore.storeRegistrationToken(registrationResponse);
    }

    private void auth(SimpleHttpClient simpleHttpClient) throws Exception {
        RegistrationResponse registrationResponse = registrationTokenStore.retrieveRegistrationToken();
        AuthenticationCookie authenticationCookie = new AuthenticateCommand(digitalPaperEndpoint, cryptographyUtils).authenticate(registrationResponse);
        authenticationCookie.insertInCookieManager(digitalPaperEndpoint.getSecuredURI(), (CookieManager) CookieHandler.getDefault());
        authenticationCookie.insertInRequest(simpleHttpClient::addDefaultHeader);
    }

    private void listDocuments() throws IOException, InterruptedException {
        DocumentListResponse documentListResponse = new DocumentCommand(digitalPaperEndpoint).listDocuments();
        documentListResponse.getEntryList().forEach(documentEntry -> logWriter.log(documentEntry.getEntryPath()));
    }

    private void listDocumentsInfo() throws IOException, InterruptedException {
        DocumentListResponse documentListResponse = new DocumentCommand(digitalPaperEndpoint).listDocuments();
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

    private void upload(String local, String remotePath) throws IOException, InterruptedException {
        Path localPath = Path.of(local);
        if (remotePath == null) {
            remotePath = "Document/Received/" + localPath.getFileName();
        }
        new DocumentCommand(digitalPaperEndpoint).upload(localPath, Path.of(remotePath));
    }

    private void deleteFolder(String remotePath) throws IOException, InterruptedException {
        new DocumentCommand(digitalPaperEndpoint).deleteFolder(Path.of(remotePath));
    }

    private void deleteDocument(String remotePath) throws IOException, InterruptedException {
        new DocumentCommand(digitalPaperEndpoint).delete(Path.of(remotePath));
    }

    private void downloadDocument(String remotePath, String localPath) throws IOException, InterruptedException {
        Path localDownloadPath;
        if (localPath == null) {
            localDownloadPath = Path.of(".");
        } else {
            localDownloadPath = Path.of(localPath);
        }
        Path remoteDownloadPath = Path.of(remotePath);
        InputStream inputStream = new DocumentCommand(digitalPaperEndpoint).download(remoteDownloadPath);

        if (Files.isDirectory(localDownloadPath)) {
            localDownloadPath = localDownloadPath.resolve(remoteDownloadPath.getFileName());
        }

        try (OutputStream outputStream = Files.newOutputStream(localDownloadPath)) {
            IOUtils.copy(inputStream, outputStream);
            inputStream.close();
        }
    }

    private void newFolder(String remotePath) throws IOException, InterruptedException {
        new DocumentCommand(digitalPaperEndpoint).createFolderRecursively(Path.of(remotePath));
    }

    private void moveDocument(String oldPath, String newPath) throws IOException, InterruptedException {
        new DocumentCommand(digitalPaperEndpoint).move(Path.of(oldPath), Path.of(newPath));
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

    /**
     * Without quoting:
     * Console arg: /mnt/bananas/books/Digital Paper Sync/
     * Path interpretation: /mnt/bananas/books/Digital Paper Sync
     *
     * With quoting (won't work):
     * Console arg: /mnt/bananas/books/Digital\ Paper\ Sync/
     * Path interpretation: /mnt/bananas/books/Digital\ Paper\ Sync
     *
     */
    private void sync(String localFolder, boolean dryrun) throws IOException, InterruptedException {
        new SyncCommand(
                Path.of(localFolder),
                new DocumentCommand(digitalPaperEndpoint),
                new DocumentCommand(digitalPaperEndpoint),
                digitalPaperEndpoint,
                logWriter,
                syncStore,
                new LocalSyncProgressBar(System.out, ProgressBar.ProgressStyle.RECTANGLES_1)
        ).sync(dryrun);
    }

    private void showOwner() throws IOException, InterruptedException {
        new SystemConfigCommand(digitalPaperEndpoint, logWriter).showOwnerName();
    }

    private void setOwner(String ownerName) throws IOException, InterruptedException {
        new SystemConfigCommand(digitalPaperEndpoint, logWriter).setOwnerName(ownerName);
    }

    private void turnWifiOff() throws IOException, InterruptedException {
        new WifiCommand(digitalPaperEndpoint, inputReader, logWriter).turnOff();
    }

    private void turnWifiOn() throws IOException, InterruptedException {
        new WifiCommand(digitalPaperEndpoint, inputReader, logWriter).turnOn();
    }

}
