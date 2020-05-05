package net.sony.dpt.command.root;

import com.android.ddmlib.*;
import net.sony.dpt.command.firmware.FirmwareVersionResponse;
import net.sony.util.LogWriter;
import net.sony.util.ProgressBar;

import java.io.IOException;
import java.nio.file.Path;
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

    private final static String[] extensionsSearchPaths = new String[] {"/etc/dp_extensions", "/data/dp_extensions"};

    public AdbCommand(final LogWriter logWriter, final ProgressBar progressBar) throws InterruptedException {
        this.logWriter = logWriter;
        this.progressBar = progressBar;
        initAdb();
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
                throw new AdbException("Impossible to connect to adb, did you install it ?");
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
                throw new AdbException("Impossible to list devices, did you connect your digital paper ?");
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

        AtomicInteger total = new AtomicInteger(0);
        AtomicBoolean finished = new AtomicBoolean(false);

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

        syncService.pull(
                new FileEntry[]{entry},
                localFolder,
                progressMonitor
        );

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            if (finished.get()) executor.shutdownNow();
        }, 0, 1, TimeUnit.SECONDS);
        executor.awaitTermination(60, TimeUnit.SECONDS);
        syncService.close();
    }

    public void tearDown() {
        AndroidDebugBridge.terminate();
    }

}
