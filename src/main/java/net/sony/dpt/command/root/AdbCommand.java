package net.sony.dpt.command.root;

import com.android.ddmlib.*;
import net.sony.util.LogWriter;
import net.sony.util.ProgressBar;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.android.ddmlib.FileListingService.*;

public class AdbCommand {

    private final LogWriter logWriter;
    private final ProgressBar progressBar;

    private static boolean isInit = false;

    private AndroidDebugBridge adb;
    private IDevice digitalPaperDevice;

    private final static String USERSPACE_EXTENSION_PATH = "/data/dp_extensions";
    private final static String[] extensionsSearchPaths = new String[] {"/etc/dp_extensions", USERSPACE_EXTENSION_PATH};
    private final static String TEMPLATE_ROOT = "root/extensions/template";

    private final static String EXTENSION_TEMPLATE_TOKEN = "EXTENSION_TEMPLATE_TOKEN";
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

    private void copyExtensionFile(final String name, final Map<String, String> replacements, final Path templatePath, final Path tempDir) throws IOException {
        logWriter.log("Preparing " + name);

        String newContent = Files.readString(templatePath.resolve(name), StandardCharsets.UTF_8);

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

    public void setupExtension(final String name, final String component, final String action, byte[] icon) throws IOException, URISyntaxException, AdbCommandRejectedException, ShellCommandUnresponsiveException, TimeoutException, InterruptedException, SyncException {
        Path tempDir = Files.createDirectories(Files.createTempDirectory("dpt").resolve(name));
        try {
            // We first copy the resources with the correct name
            URL templateURL = AdbCommand.class.getClassLoader().getResource(TEMPLATE_ROOT);
            AtomicBoolean failed = new AtomicBoolean(false);

            logWriter.log("Preparing extension into " + tempDir);
            Path templatePath = Path.of(Objects.requireNonNull(templateURL).toURI());

            Map<String, String> replacements = new HashMap<>() {{
                put(EXTENSION_TEMPLATE_TOKEN, name);
                put(EXTENSION_TEMPLATE_LOWER, name.toLowerCase());
                put(EXTENSION_TEMPLATE_ENGLISH_NAME, name);
                put(EXTENSION_TEMPLATE_JAPANESE_NAME, name);
                put(EXTENSION_TEMPLATE_PUTONGHUA_NAME, name);
                put(EXTENSION_TEMPLATE_ACTIVITY_PATH, component);
                put(EXTENSION_TEMPLATE_ACTION, action);
            }};
            copyExtensionFile(EXTENSION_XML, replacements, templatePath, tempDir);
            copyExtensionFile(EXTENSION_STRINGS_EN, replacements, templatePath, tempDir);
            copyExtensionFile(EXTENSION_STRINGS_JA, replacements, templatePath, tempDir);
            copyExtensionFile(EXTENSION_STRINGS_PUTONGHUA, replacements, templatePath, tempDir);

            Files.copy(templatePath.resolve(EXTENSION_ICON), tempDir.resolve(EXTENSION_ICON.replaceAll(EXTENSION_TEMPLATE_LOWER, name.toLowerCase())));

            // Now that everything is ready, we need to push the extension. First let's see if it was there already
            createFolderIfNotExists(USERSPACE_EXTENSION_PATH);
            FileEntry extensionEntry = isInPath(USERSPACE_EXTENSION_PATH, name);
            if (extensionEntry != null) deleteFolderIfExists(USERSPACE_EXTENSION_PATH + "/name");

            AtomicBoolean finished = new AtomicBoolean(false);

            SyncService syncService = digitalPaperDevice.getSyncService();

            syncService.push(new String[] {tempDir.toString()}, entry(USERSPACE_EXTENSION_PATH), progressMonitor(finished));

            waitForFinished(finished, 30);
            syncService.close();

            refreshExtensionDB();

            // And we reboot
            digitalPaperDevice.reboot(null);
        } finally {
            FileUtils.deleteDirectory(tempDir.toFile());
        }
    }

    public void removeExtension(final String name) throws InterruptedException, TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
        // First we scan in the userspace folder
        FileEntry extension = isInPath(USERSPACE_EXTENSION_PATH, name);
        if (extension != null) {
            logWriter.log("Found " + name + " in " + USERSPACE_EXTENSION_PATH);
        } else {
            logWriter.log("We couldn't find " + name + " in " + USERSPACE_EXTENSION_PATH + ", abandonning. To delete extensions in the kernel space, remount and delete manually.");
            return;
        }

        deleteFolderIfExists(USERSPACE_EXTENSION_PATH + "/" + name);
        refreshExtensionDB();
        logWriter.log("Successfully uninstalled, rebooting...");
        digitalPaperDevice.reboot(null);
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

    private void waitForFinished(final AtomicBoolean finished, final int timeoutSeconds) throws InterruptedException {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            if (finished.get()) executor.shutdownNow();
        }, 0, 1, TimeUnit.SECONDS);
        executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
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
        String dbPath = "/data/system/ExtMgr.db";
        String dbJournalPath = "/data/system/ExtMgr.db-journal";

        executeShellCommandSync("mv " + dbPath + " " + dbPath + ".backup");
        executeShellCommandSync("mv " + dbJournalPath + " " + dbPath + ".backup");
    }

    public void tearDown() {
        AndroidDebugBridge.terminate();
    }

}
