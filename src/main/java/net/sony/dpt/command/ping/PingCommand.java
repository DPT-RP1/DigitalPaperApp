package net.sony.dpt.command.ping;

import net.sony.dpt.network.DigitalPaperEndpoint;
import net.sony.util.LogWriter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;

public class PingCommand {

    private static final int TIMEOUT = 5000;
    private DigitalPaperEndpoint digitalPaperEndpoint;
    private LogWriter logWriter;

    public PingCommand() {
    }

    public PingCommand(final DigitalPaperEndpoint digitalPaperEndpoint, final LogWriter logWriter) {
        this.digitalPaperEndpoint = digitalPaperEndpoint;
        this.logWriter = logWriter;
    }

    public boolean ping(String ip) throws IOException {
        InetAddress deviceIp = InetAddress.getByName(ip);
        return deviceIp.isReachable(TIMEOUT);
    }

    public boolean ping() throws URISyntaxException, IOException {
        URI uri = digitalPaperEndpoint.getSecuredURI();
        boolean reachable = ping(uri.getHost());
        if (reachable) {
            logWriter.log("Discovered a Digital Paper at " + uri);
        } else {
            logWriter.log("No Digital Paper detected...");
        }
        return reachable;
    }

    public boolean pingQuiet() throws IOException, URISyntaxException {
        URI uri = digitalPaperEndpoint.getSecuredURI();
        return ping(uri.getHost());
    }

    public String pingAndResolve(String hostname) throws IOException {
        if (ping(hostname)) {
            try {
                InetAddress inet = InetAddress.getByName(hostname);
                return inet.getHostAddress();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
