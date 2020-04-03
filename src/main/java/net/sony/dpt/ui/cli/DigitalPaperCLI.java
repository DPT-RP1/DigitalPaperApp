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
import net.sony.dpt.command.ping.PingCommand;
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

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class DigitalPaperCLI {

    private final Options options;
    private final CommandLineParser parser;
    private final SimpleHttpClient simpleHttpClient;
    private final DiffieHelman diffieHelman;
    private final CryptographyUtil cryptographyUtil;
    private final LogWriter logWriter;
    private final InputReader inputReader;
    private final RegistrationTokenStore registrationTokenStore;
    private DigitalPaperEndpoint digitalPaperEndpoint;
    private final SyncStore syncStore;
    private final DeviceInfoStore deviceInfoStore;

    public DigitalPaperCLI(SimpleHttpClient simpleHttpClient,
                           DiffieHelman diffieHelman,
                           CryptographyUtil cryptographyUtil,
                           LogWriter logWriter,
                           InputReader inputReader,
                           RegistrationTokenStore registrationTokenStore,
                           SyncStore syncStore,
                           DeviceInfoStore deviceInfoStore) {

        parser = new DefaultParser();
        this.simpleHttpClient = simpleHttpClient;
        this.diffieHelman = diffieHelman;
        this.cryptographyUtil = cryptographyUtil;
        this.logWriter = logWriter;
        this.inputReader = inputReader;
        this.registrationTokenStore = registrationTokenStore;
        this.syncStore = syncStore;
        this.deviceInfoStore = deviceInfoStore;

        options = new Options();
        options.addOption("addr", "addr", true, "The ip address of the Digital Paper");
        options.addOption("serial", "serial", true, "The serial number of the Digital Paper we want to auto discover");
        options.addOption("dryrun", "dryrun", false, "For commands that can run in dry mode, simulate their action");
    }

    private String findAddress(CommandLine commandLine) throws IOException, InterruptedException {
        String addr;
        if (commandLine.hasOption("addr")) {
            addr = commandLine.getOptionValue("addr");
        } else {
            // Before trying autoconfig, we can try loading the last ip
            String lastIp = deviceInfoStore.retrieveLastIp();
            if (lastIp != null && new PingCommand(simpleHttpClient).pingIp(lastIp)) {
                addr = lastIp;
            } else {
                String matchSerial = null;
                if (commandLine.hasOption("serial")) {
                    matchSerial = commandLine.getOptionValue("serial");
                }
                addr = new FindDigitalPaper(logWriter, simpleHttpClient, matchSerial).findOneIpv4();
            }
        }
        if (addr == null || addr.isEmpty()) throw new IllegalStateException("No device found or reachable.");
        // We store the last address
        deviceInfoStore.storeLastIp(addr);
        return addr;
    }

    public void execute(String[] args) throws Exception {
        CommandLine commandLine = parser.parse(options, args);

        String addr = findAddress(commandLine);
        digitalPaperEndpoint = new DigitalPaperEndpoint(addr, simpleHttpClient);

        // The arguments have to be ordered: command param1 param2 etc.
        List<String> arguments = commandLine.getArgList();
        String command = arguments.get(0);
        if (command.equals("register")) {
            register(addr);
            return;
        }

        auth();

        switch (command) {
            case "list-documents":
                listDocuments();
                break;
            case "document-info":
                listDocumentsInfo();
                break;
            case "wifi-list":
                wifiList();
                break;
            case "upload":
                String localPath = arguments.get(1);
                String remotePath = null;
                if (arguments.size() > 2) {
                    remotePath = arguments.get(2);
                }
                upload(localPath, remotePath);
                break;
            case "delete-folder":
                deleteFolder(arguments.get(1));
                break;
            case "delete":
                deleteDocument(arguments.get(1));
                break;
            case "download":
                String localDownloadPath = null;
                if (arguments.size() > 2) {
                    localDownloadPath = arguments.get(2);
                }
                downloadDocument(arguments.get(1), localDownloadPath);
                break;
            case "new-folder":
                newFolder(arguments.get(1));
                break;
            case "move-document":
                moveDocument(arguments.get(1), arguments.get(2));
                break;
            case "wifi-scan":
                wifiScan();
                break;
            case "screenshot":
                takeScreenshot(arguments.get(1));
                break;
            case "whiteboard":
                new Whiteboard(new TakeScreenshotCommand(digitalPaperEndpoint));
                break;
            case "sync":
                sync(arguments.get(1), commandLine.hasOption("dryrun"));
                break;
            case "dialog":
                showDialog(arguments.get(1), arguments.get(2), arguments.get(3));
                break;
            case "get-owner":
            case "show-owner":
                showOwner();
                break;
            case "set-owner":
                setOwner(arguments.get(1));
                break;
            case "copy-document":
            case "wifi-add":
            case "wifi-del":
            case "wifi":
            case "wifi-enable":
            case "wifi-disable":
            case "update-firmware":
            case "command-help":
                throw new UnsupportedOperationException(command);

        }

    }

    private void showDialog(String title, String text, String buttonText) throws IOException, InterruptedException {
        new DialogCommand(digitalPaperEndpoint).show(title, text, buttonText);
    }

    private void register(String addr) throws IOException, BadPaddingException, InterruptedException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException, InvalidKeyException {
        RegistrationResponse registrationResponse = new RegisterCommand(addr, simpleHttpClient, diffieHelman, cryptographyUtil, logWriter, inputReader).register();
        registrationTokenStore.storeRegistrationToken(registrationResponse);
    }

    private void auth() throws Exception {
        RegistrationResponse registrationResponse = registrationTokenStore.retrieveRegistrationToken();
        AuthenticationCookie authenticationCookie = new AuthenticateCommand(digitalPaperEndpoint, cryptographyUtil).authenticate(registrationResponse);
        authenticationCookie.insertInCookieManager(digitalPaperEndpoint.getURI(), (CookieManager) CookieHandler.getDefault());
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
        WifiCommand wifiCommand = new WifiCommand(digitalPaperEndpoint);
        AccessPointList accessPointList = wifiCommand.list();
        accessPointList.getAccessPoints().forEach(accessPoint -> logWriter.log(accessPoint.getDecodedSSID()));
    }

    private void wifiScan() throws IOException, InterruptedException {
        WifiCommand wifiCommand = new WifiCommand(digitalPaperEndpoint);
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
        new TransferDocumentCommand(digitalPaperEndpoint).moveDocument(Path.of(oldPath), Path.of(newPath));
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
                new LocalSyncProgressBar(logWriter, RemoteSyncProgressBar.ProgressStyle.RECTANGLES_1)
        ).sync(dryrun);
    }

    private void showOwner() throws IOException, InterruptedException {
        new StatusCommand(digitalPaperEndpoint, logWriter).showOwnerName();
    }

    private void setOwner(String ownerName) throws IOException, InterruptedException {
        new StatusCommand(digitalPaperEndpoint, logWriter).setOwnerName(ownerName);
    }

}
