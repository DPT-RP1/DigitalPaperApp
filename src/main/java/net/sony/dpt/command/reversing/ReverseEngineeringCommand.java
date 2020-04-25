package net.sony.dpt.command.reversing;

import net.sony.dpt.network.DigitalPaperEndpoint;
import net.sony.util.LogWriter;

import java.io.IOException;

public class ReverseEngineeringCommand {

    private final DigitalPaperEndpoint digitalPaperEndpoint;
    private final LogWriter logWriter;

    public ReverseEngineeringCommand(final DigitalPaperEndpoint digitalPaperEndpoint, final LogWriter logWriter) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
        this.logWriter = logWriter;
    }

    public void sendGet(final String url) throws IOException, InterruptedException {
        logWriter.log(digitalPaperEndpoint.rawSecuredGet(url));
    }

}
