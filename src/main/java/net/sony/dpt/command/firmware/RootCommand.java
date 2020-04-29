package net.sony.dpt.command.firmware;

import net.sony.dpt.root.RootPacker;
import net.sony.util.LogWriter;

import java.io.IOException;
import java.net.URISyntaxException;

public class RootCommand {

    private final FirmwareUpdatesCommand firmwareUpdatesCommand;
    private final RootPacker rootPacker;
    private final LogWriter logWriter;

    public RootCommand(final LogWriter logWriter, final FirmwareUpdatesCommand firmwareUpdatesCommand, final RootPacker rootPacker) {
        this.firmwareUpdatesCommand = firmwareUpdatesCommand;
        this.rootPacker = rootPacker;
        this.logWriter = logWriter;
    }

    public void rootDevice(boolean dryrun) throws IOException, URISyntaxException, InterruptedException {
        logWriter.log("Creating root package...");
        byte[] firmwareData = rootPacker.createStandardRootPackage();
        logWriter.log("Root package created: " + firmwareData.length + " bytes");
        firmwareUpdatesCommand.updateAny(dryrun, firmwareData);
    }
}
