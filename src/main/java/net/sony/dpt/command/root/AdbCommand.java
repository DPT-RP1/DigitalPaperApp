package net.sony.dpt.command.root;

import com.android.ddmlib.*;
import net.dongliu.apk.parser.ApkFile;
import net.sony.dpt.data.extmgr.ExtMgrDao;
import net.sony.util.ImageUtils;
import net.sony.util.LogWriter;
import net.sony.util.ProgressBar;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.sqlite.core.DB;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.android.ddmlib.FileListingService.*;
import static net.sony.util.AsyncUtils.waitForFinished;

public class AdbCommand {

    private final LogWriter logWriter;
    private final ProgressBar progressBar;

    private static boolean isInit = false;

    private AndroidDebugBridge adb;
    private IDevice digitalPaperDevice;

    private final static String USERSPACE_EXTENSION_PATH = "/data/dp_extensions";
    private final static String[] extensionsSearchPaths = new String[] {"/etc/dp_extensions", USERSPACE_EXTENSION_PATH};
    private final static String TEMPLATE_ROOT = "root/extensions/template";
    private final static String DB_NAME = "ExtMgr.db";
    private final static String DB_JOURNAL_NAME = DB_NAME + "-journal";
    private final static String DB_PATH = "/data/system/" + DB_NAME;
    private final static String DB_JOURNAL_PATH = "/data/system/" + DB_JOURNAL_NAME;
    private final static String DEFAULT_ICON_ID = "STR_ICONMENU_1001";

    private final static String EXTENSION_TEMPLATE_TOKEN = "EXTENSION_TEMPLATE_TOKEN";
    private final static String EXTENSION_URI_TOKEN = "EXTENSION_URI_TOKEN";
    private final static String EXTENSION_TEMPLATE_LOWER = "EXTENSION_TEMPLATE_lower";
    private final static String EXTENSION_TEMPLATE_ACTIVITY_PATH = "EXTENSION_TEMPLATE_ACTIVITY_PATH";
    private final static String EXTENSION_TEMPLATE_ACTION = "EXTENSION_TEMPLATE_ACTION";
    private final static String EXTENSION_XML = "EXTENSION_TEMPLATE_TOKEN_extension.xml";
    private final static String EXTENSION_STRINGS_EN = "EXTENSION_TEMPLATE_TOKEN_strings-en.xml";
    private final static String EXTENSION_TEMPLATE_ENGLISH_NAME = "EXTENSION_TEMPLATE_ENGLISH_NAME";
    private final static String EXTENSION_STRINGS_JA = "EXTENSION_TEMPLATE_TOKEN_strings-ja.xml";
    private final static String EXTENSION_TEMPLATE_JAPANESE_NAME = "EXTENSION_TEMPLATE_JAPANESE_NAME";
    private final static String EXTENSION_STRINGS_PUTONGHUA = "EXTENSION_TEMPLATE_TOKEN_strings-zh_CN.xml";
    private final static String EXTENSION_TEMPLATE_PUTONGHUA_NAME = "EXTENSION_TEMPLATE_PUTONGHUA_NAME";
    private final static String EXTENSION_ICON = "ic_homemenu_EXTENSION_TEMPLATE_lower.png";
    private final static String EXTENSION_URI = "intent:#Intent;component=EXTENSION_TEMPLATE_ACTIVITY_PATH;action=EXTENSION_TEMPLATE_ACTION;end";


    public AdbCommand(final LogWriter logWriter, final ProgressBar progressBar) throws InterruptedException {
        this.logWriter = logWriter;
        this.progressBar = progressBar;
        try {
            initAdb();
        } catch (AdbException e) {
            tearDown();
            throw e;
        }
    }

    public AdbCommand(final LogWriter logWriter, final ProgressBar progressBar, final AndroidDebugBridge androidDebugBridge, final IDevice device) {
        this.logWriter = logWriter;
        this.progressBar = progressBar;
        adb = androidDebugBridge;
        digitalPaperDevice = device;
    }

    public void initAdb() throws InterruptedException {
        if (!isInit) {
            AndroidDebugBridge.init(false);
            adb = AndroidDebugBridge.createBridge();

            int trials = 10;
            while (trials > 0) {
                Thread.sleep(50);
                if (adb.isConnected()) {
                    break;
                }
                trials--;
            }

            if(!adb.isConnected()) {
                throw new AdbException("Impossible to connect to adb, did you install it, is your device plugged via USB ?");
            }

            trials = 10;
            while (trials > 0) {
                Thread.sleep(50);
                if (adb.hasInitialDeviceList()) {
                    break;
                }
                trials--;
            }

            if (!adb.hasInitialDeviceList()) {
                throw new AdbException("Impossible to list devices, did you connect your digital paper, did you kill other adb instances ?");
            }

            IDevice[] devices = adb.getDevices();

            if (devices.length == 0) {
                throw new AdbException("Impossible to list devices, did you connect your digital paper ?");
            }

            for (IDevice device : devices) {
                if (device.getProperty("ro.product.name").equals("FPX_1010")) {
                    digitalPaperDevice = device;
                    break;
                } else {
                    logWriter.log("Found a non-DPT device: " + device.getSerialNumber());
                }
            }
            if (digitalPaperDevice == null) {
                throw new AdbException("Impossible to find a DPT (ro.product.name = FPX_1010), did you connect your digital paper ?");
            }

        }
    }

    public FileEntry entry(String remotePath) {

        FileListingService listingService = digitalPaperDevice.getFileListingService();

        FileEntry entry = listingService.getRoot();
        String[] segments = remotePath.split("/");

        for(String segment : segments) {
            if (!segment.isEmpty()) {
                listingService.getChildren(entry, false, null);
                entry = entry.findChild(segment);
            }
        }

        return entry;
    }

    private FileEntry isInPath(final String searchPath, final String name) {
        FileEntry searchEntry = entry(searchPath);
        if (searchEntry == null) return null;

        FileEntry[] children = digitalPaperDevice.getFileListingService().getChildren(searchEntry, false, null);
        if (children == null) return null;

        for (FileEntry child : children) {
            if (child.getName().equals(name)) return child;
        }
        return null;
    }

    private void showExtensionsDescriptors(final String searchPath) {
        logWriter.log("Listing extensions in " + searchPath + ": ");
        FileEntry searchEntry = entry(searchPath);
        if (searchEntry == null) {
            logWriter.log("No extensions found...");
            return;
        }

        FileEntry[] entries = digitalPaperDevice.getFileListingService().getChildren(searchEntry, false, null);
        if (entries != null) {
            for (FileEntry entry : entries) {
                logWriter.log(entry.getName());
            }
        }
        logWriter.log("");
    }

    public void showExtensionsDescriptors() {
        for (String searchPath : extensionsSearchPaths) {
            showExtensionsDescriptors(searchPath);
        }
    }

    private SyncService.ISyncProgressMonitor progressMonitor(final AtomicBoolean finished) {
        AtomicInteger total = new AtomicInteger(0);

        SyncService.ISyncProgressMonitor progressMonitor = null;
        if (progressBar != null) {
            progressMonitor = new SyncService.ISyncProgressMonitor() {
                @Override
                public void start(int totalWork) {
                    progressBar.start();
                    progressBar.progressed(0, totalWork);
                    total.set(totalWork);
                }

                @Override
                public void stop() {
                    progressBar.stop();
                    finished.set(true);
                }

                @Override public boolean isCanceled() { return false; }

                @Override public void startSubTask(String name) {
                    progressBar.current(name);
                }

                @Override
                public void advance(int work) {
                    progressBar.progressed(work, total.get());
                }
            };
        }
        return progressMonitor;
    }

    public void downloadExtensionsDescriptor(String name, Path localTarget) throws AdbCommandRejectedException, IOException, TimeoutException, SyncException, InterruptedException {
        FileEntry entry = null;
        for (String searchPath : extensionsSearchPaths) {
            entry = isInPath(searchPath, name);
            if (entry != null) {
                break;
            }
        }
        if (entry == null) {
            logWriter.log("Could not find extension " + name + " on the device...");
        }

        SyncService syncService = digitalPaperDevice.getSyncService();

        String localFolder = localTarget.toString();

        AtomicBoolean finished = new AtomicBoolean(false);

        syncService.pull(
                new FileEntry[]{entry},
                localFolder,
                progressMonitor(finished)
        );

        waitForFinished(finished, 30);
        syncService.close();
    }

    private void copyExtensionFile(final String name, final Map<String, String> replacements, final String templateRootResource, final Path tempDir) throws IOException {
        logWriter.log("Preparing " + name);

        String newContent = IOUtils.toString(Objects.requireNonNull(AdbCommand.class.getClassLoader().getResourceAsStream(templateRootResource + "/" + name)), StandardCharsets.UTF_8);

        for (String key : replacements.keySet()) {
            newContent = newContent.replaceAll(key, replacements.get(key));
        }

        logWriter.log(newContent);

        Files.writeString(
                tempDir.resolve(
                        name.replaceAll(EXTENSION_TEMPLATE_TOKEN, replacements.get(EXTENSION_TEMPLATE_TOKEN))
                ),
                newContent
        );
    }

    public void setupExtension(final String name, final String component, final String action, byte[] icon) throws IOException, AdbCommandRejectedException, ShellCommandUnresponsiveException, TimeoutException, InterruptedException, SyncException {
        Path tempDir = Files.createDirectories(Files.createTempDirectory("dpt").resolve(name));
        try {
            // We first copy the resources with the correct name
            logWriter.log("Preparing extension into " + tempDir);

            String uri = EXTENSION_URI
                    .replaceAll(EXTENSION_TEMPLATE_ACTION, action)
                    .replaceAll(EXTENSION_TEMPLATE_ACTIVITY_PATH, component);
            Map<String, String> replacements = new HashMap<>() {{
                put(EXTENSION_TEMPLATE_TOKEN, name);
                put(EXTENSION_TEMPLATE_LOWER, name.toLowerCase());
                put(EXTENSION_TEMPLATE_ENGLISH_NAME, name);
                put(EXTENSION_TEMPLATE_JAPANESE_NAME, name);
                put(EXTENSION_TEMPLATE_PUTONGHUA_NAME, name);
                put(EXTENSION_TEMPLATE_ACTIVITY_PATH, component);
                put(EXTENSION_TEMPLATE_ACTION, action);
                put(EXTENSION_URI_TOKEN, uri);

            }};
            copyExtensionFile(EXTENSION_XML, replacements, TEMPLATE_ROOT, tempDir);
            copyExtensionFile(EXTENSION_STRINGS_EN, replacements, TEMPLATE_ROOT, tempDir);
            copyExtensionFile(EXTENSION_STRINGS_JA, replacements, TEMPLATE_ROOT, tempDir);
            copyExtensionFile(EXTENSION_STRINGS_PUTONGHUA, replacements, TEMPLATE_ROOT, tempDir);

            if (icon == null || icon.length == 0) {
                icon = IOUtils.toByteArray(
                        Objects.requireNonNull(
                                AdbCommand.class.getClassLoader().getResourceAsStream(TEMPLATE_ROOT + "/" + EXTENSION_ICON)
                        ));
            }

            String iconName = EXTENSION_ICON.replaceAll(
                    EXTENSION_TEMPLATE_LOWER,
                    name.toLowerCase()
            );

            Files.write(
                    tempDir.resolve(iconName),
                    icon
            );

            // Now that everything is ready, we need to push the extension. First let's see if it was there already
            createFolderIfNotExists(USERSPACE_EXTENSION_PATH);
            FileEntry extensionEntry = isInPath(USERSPACE_EXTENSION_PATH, name);

            String extensionTargetFolder = USERSPACE_EXTENSION_PATH + "/" + name;
            if (extensionEntry != null) deleteFolderIfExists(extensionTargetFolder);

            AtomicBoolean finished = new AtomicBoolean(false);

            SyncService syncService = digitalPaperDevice.getSyncService();

            syncService.push(new String[] {tempDir.toString()}, entry(USERSPACE_EXTENSION_PATH), progressMonitor(finished));

            waitForFinished(finished, 30);
            syncService.close();

            String remoteIconPath = extensionTargetFolder + "/" + iconName;

            String localDBPath = tempDir.resolve(DB_NAME).toString();
            String localDBJournalPath = tempDir.resolve(DB_JOURNAL_NAME).toString();
            digitalPaperDevice.pullFile(DB_PATH, localDBPath);

            Path dbPath = tempDir.resolve(DB_NAME);
            try (ExtMgrDao extMgrDao = new ExtMgrDao(dbPath, logWriter)){
                extMgrDao.insertExtension(name, extensionTargetFolder, uri, DEFAULT_ICON_ID, remoteIconPath, ExtMgrDao.Order.INSERT_LAST);
            } catch (Exception e) {
                logWriter.log("Something went wrong when editing the database, we'll delete it entirely and let the device recreate it." + e.getMessage());
                refreshExtensionDB();
                digitalPaperDevice.reboot(null);
            }

            logWriter.log("Writing to remote launcher DB");
            digitalPaperDevice.pushFile(localDBPath, DB_PATH);

            logWriter.log("Rebooting");
            digitalPaperDevice.reboot(null);

        } finally {
            FileUtils.deleteDirectory(tempDir.toFile());
        }
    }

    public void removeExtension(final String name) throws InterruptedException, TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException, SyncException {
        // First we scan in the userspace folder
        FileEntry extension = isInPath(USERSPACE_EXTENSION_PATH, name);
        if (extension != null) {
            logWriter.log("Found " + name + " in " + USERSPACE_EXTENSION_PATH);
        } else {
            logWriter.log("We couldn't find " + name + " in " + USERSPACE_EXTENSION_PATH + ", abandonning. To delete extensions in the kernel space, remount and delete manually.");
            return;
        }

        deleteFolderIfExists(USERSPACE_EXTENSION_PATH + "/" + name);

        Path tempDir = Files.createTempDirectory("dpt");
        try {
            String localDBPath = tempDir.resolve(DB_NAME).toString();
            String localDBJournalPath = tempDir.resolve(DB_JOURNAL_NAME).toString();
            digitalPaperDevice.pullFile(DB_PATH, localDBPath);

            Path dbPath = tempDir.resolve(DB_NAME);
            try (ExtMgrDao extMgrDao = new ExtMgrDao(dbPath, logWriter)) {
                extMgrDao.deleteIfExists(name);
            } catch (Exception e) {
                logWriter.log("Something went wrong when editing the database, we'll delete it entirely and let the device recreate it: " + e.getMessage());
                refreshExtensionDB();
                // And we reboot
                digitalPaperDevice.reboot(null);
                return;
            }

            logWriter.log("Writing to remote launcher DB");
            digitalPaperDevice.pushFile(localDBPath, DB_PATH);

            logWriter.log("Rebooting");
            digitalPaperDevice.reboot(null);
        } finally {
            FileUtils.deleteDirectory(tempDir.toFile());
        }
    }

    private void deleteFolderIfExists(String folderToDelete) throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException, InterruptedException {
        if (folderToDelete.equals("/")) throw new IllegalArgumentException("Should not delete " + folderToDelete);
        executeShellCommandSync("rm -rf " + folderToDelete);
    }

    private void createFolderIfNotExists(final String userspaceExtensionPath) throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException, InterruptedException {
        executeShellCommandSync("mkdir -p " + userspaceExtensionPath);
    }

    private void executeShellCommandSync(final String command) throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException, InterruptedException {
        AtomicBoolean finished = new AtomicBoolean(false);
        digitalPaperDevice.executeShellCommand(command, shellOutputReceiver(finished));
        waitForFinished(finished, 30);
    }

    private IShellOutputReceiver shellOutputReceiver(final AtomicBoolean finished) {
        return new IShellOutputReceiver() {
            @Override
            public void addOutput(byte[] data, int offset, int length) {
                logWriter.log(new String(ArrayUtils.subarray(data, offset, length)));
            }
            @Override public void flush() { finished.set(true);}
            @Override public boolean isCancelled() { return false; }
        };
    }

    private void refreshExtensionDB() throws InterruptedException, TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
        executeShellCommandSync("mv " + DB_PATH + " " + DB_PATH + ".backup");
        executeShellCommandSync("mv " + DB_JOURNAL_PATH + " " + DB_JOURNAL_PATH + ".backup");
    }

    private void restartLauncher() throws InterruptedException, TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
        executeShellCommandSync("am stopservice -n com.sony.apps.applauncher/.presentation.service.impl.LaunchViewService");
        executeShellCommandSync("am startservice -n com.sony.apps.applauncher/.presentation.service.impl.LaunchViewService");
    }

    public void installApk(final Path apkLocation) throws IOException, ParserConfigurationException, SAXException, XPathExpressionException, InstallException, AdbCommandRejectedException, InterruptedException, SyncException, ShellCommandUnresponsiveException, TimeoutException, URISyntaxException {
        // We need an activity and an action
        // Activity: /manifest/android:package + / + /manifest/application/activity/android:name
        // Action: we take the first intent filter

        logWriter.log("Loading APK");
        ApkFile apkFile = new ApkFile(apkLocation.toFile());

        logWriter.log("Parsing APK");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new StringBufferInputStream(apkFile.getManifestXml()));

        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();

        String packageDefinition = apkFile.getApkMeta().getPackageName();

        XPathExpression expr = xpath.compile("/manifest/application/activity/@name");
        String activityName = expr.evaluate(doc);

        expr = xpath.compile("/manifest/application/activity/intent-filter/action/@name");
        String action = expr.evaluate(doc);

        expr = xpath.compile("/manifest/application/@icon");
        String iconPath = expr.evaluate(doc);

        logWriter.log("Resizing icon to 220 x 120");
        byte[] icon = apkFile.getFileData(iconPath);
        icon = ImageUtils.resize(icon, "png", 220, 120);
        String apkName = apkFile.getApkMeta().getName();

        logWriter.log("Sending APK to the DPT");
        // We can now trigger the install process with adb
        digitalPaperDevice.installPackage(apkLocation.toString(), true);

        logWriter.log("Setting up extension (App Launcher)");
        // Now that we're installed, we can create a new extension
        setupExtension(apkName, packageDefinition + "/" + activityName, action, icon);
    }

    public void tearDown() {
        AndroidDebugBridge.terminate();
    }

}
