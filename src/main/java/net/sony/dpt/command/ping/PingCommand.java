package net.sony.dpt.command.ping;

import net.sony.dpt.DigitalPaperEndpoint;
import net.sony.util.LogWriter;
import net.sony.util.SimpleHttpClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;

public class PingCommand {

    private DigitalPaperEndpoint digitalPaperEndpoint;
    private LogWriter logWriter;

    public PingCommand() {
    }

    public PingCommand(final DigitalPaperEndpoint digitalPaperEndpoint, final LogWriter logWriter) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
        this.logWriter = logWriter;
    }

    /**
     * This command returns true if we're still authenticated
     *
     * @return
     */
    public boolean pingAuth() {
        throw new UnsupportedOperationException("Authenticated ping not implemented");
    }

    public boolean pingIp(String ip) throws IOException {
        InetAddress deviceIp = InetAddress.getByName(ip);
        return deviceIp.isReachable(5000);
    }

    public void ping() throws URISyntaxException, IOException {
        URI uri = digitalPaperEndpoint.getURI();
        if (InetAddress.getByName(uri.getHost()).isReachable(5000)) {
            logWriter.log("Discovered a Digital Paper at " + uri);
        } else {
            logWriter.log("No Digital Paper detected...");
        }
    }
}
