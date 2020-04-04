package net.sony.dpt.command.device;

import net.sony.dpt.DigitalPaperEndpoint;
import net.sony.util.LogWriter;

import java.io.IOException;

public class StatusCommand {

    private final DigitalPaperEndpoint digitalPaperEndpoint;
    private final LogWriter logWriter;

    public StatusCommand(final DigitalPaperEndpoint digitalPaperEndpoint, final LogWriter logWriter) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
        this.logWriter = logWriter;
    }

    public String getOwnerName() throws IOException, InterruptedException {
        return digitalPaperEndpoint.getOwnerName();
    }

    public void setOwnerName(String name) throws IOException, InterruptedException {
        digitalPaperEndpoint.setOwnerName(name);
    }

    public void showOwnerName() throws IOException, InterruptedException {
        logWriter.log("Configured owner name: " + getOwnerName());
    }

}
