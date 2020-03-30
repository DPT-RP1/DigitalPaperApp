package net.sony.dpt.command.ping;

import net.sony.dpt.DigitalPaperEndpoint;
import net.sony.util.SimpleHttpClient;

import java.io.IOException;
import java.net.InetAddress;

public class PingCommand {

    private final SimpleHttpClient simpleHttpClient;

    public PingCommand(final SimpleHttpClient simpleHttpClient) {
        this.simpleHttpClient = simpleHttpClient;
    }

    /**
     * This command returns true if we're still authenticated
     *
     * @param digitalPaperEndpoint
     * @return
     */
    public boolean pingAuth(DigitalPaperEndpoint digitalPaperEndpoint) {
        throw new UnsupportedOperationException("Authenticated ping not implemented");
    }

    public boolean pingIp(String ip) throws IOException {
        InetAddress deviceIp = InetAddress.getByName(ip);
        return deviceIp.isReachable(5000);
    }
}
