package net.sony.dpt.zeroconf;

import net.sony.dpt.command.ping.PingCommand;
import net.sony.dpt.network.CheckedHttpClient;
import net.sony.dpt.network.UncheckedHttpClient;
import net.sony.dpt.persistence.DeviceInfoStore;
import net.sony.util.LogWriter;
import net.sony.dpt.network.SimpleHttpClient;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FindDigitalPaper {
    public final static String ZEROCONF_HOST = "digitalpaper.local";
    private final static int TIMEOUT_SLICES_MS = 500;
    private final static String SERVICE_TYPE = "_digitalpaper._tcp.local.";
    private static final String BIND_IP = "0.0.0.0";
    private static final String SERVICE_NAME = "Digital Paper DPT-RP1";
    private final LogWriter logWriter;
    private final DigitalPaperServiceListener digitalPaperServiceListener;

    public FindDigitalPaper(LogWriter logWriter, SimpleHttpClient simpleHttpClient, String matchSerial) {
        this.logWriter = logWriter;
        digitalPaperServiceListener = new DigitalPaperServiceListener(logWriter, simpleHttpClient, matchSerial);
    }

    public Map<InetAddress, ServiceEvent> find(int timeoutMs, boolean stopOnFirstIpv4Found) throws IOException, InterruptedException {
        // Create a JmDNS instance
        JmDNS jmdns = JmDNS.create(InetAddress.getByName(BIND_IP), SERVICE_NAME);

        // Add a service listener
        jmdns.addServiceListener(SERVICE_TYPE, digitalPaperServiceListener);

        // Wait a bit
        int currentWaitTime = 0;
        while (currentWaitTime < timeoutMs) {
            if (stopOnFirstIpv4Found && digitalPaperServiceListener.ipv4Found()) {

                kill(jmdns);
                return digitalPaperServiceListener.firstIpv4Found();
            }

            Thread.sleep(TIMEOUT_SLICES_MS);
            currentWaitTime += TIMEOUT_SLICES_MS;
        }

        kill(jmdns);
        digitalPaperServiceListener.digitalPapersDiscovered.forEach((inetAddress, ignored) -> logWriter.log("Ready to connect to " + inetAddress));
        return digitalPaperServiceListener.digitalPapersDiscovered;
    }

    private void kill(JmDNS jmDNS) throws IOException {
        jmDNS.removeServiceListener(SERVICE_TYPE, digitalPaperServiceListener);
        jmDNS.close();
    }

    public String findOneIpv4() throws IOException, InterruptedException {
        Map<InetAddress, ServiceEvent> inetAddresses = find(20000, true);
        if (!inetAddresses.isEmpty()) {
            for (InetAddress inetAddress : inetAddresses.keySet()) {
                if (inetAddress instanceof Inet4Address) {
                    Inet4Address inet4Address = (Inet4Address) inetAddress;
                    logWriter.log("Found a Digital Paper at " + inet4Address.getHostAddress());
                    return inet4Address.getHostAddress();
                }
            }
        }
        return null;
    }

    private static class DigitalPaperServiceListener implements ServiceListener {

        private static final String INFO_URL = "/register/serial_number";
        private final Map<InetAddress, ServiceEvent> digitalPapersDiscovered;
        private final LogWriter logWriter;
        private final ConcurrentMap<Inet4Address, ServiceEvent> ipv4Found;
        private final SimpleHttpClient simpleHttpClient;
        private final String matchSerial;

        public DigitalPaperServiceListener(LogWriter logWriter, SimpleHttpClient simpleHttpClient, String matchSerial) {
            digitalPapersDiscovered = new HashMap<>();
            this.logWriter = logWriter;
            ipv4Found = new ConcurrentHashMap<>();
            this.simpleHttpClient = simpleHttpClient;
            this.matchSerial = matchSerial;
        }

        public boolean ipv4Found() {
            return !ipv4Found.isEmpty();
        }

        public Map<InetAddress, ServiceEvent> firstIpv4Found() {
            Map.Entry<Inet4Address, ServiceEvent> firstEntry = ipv4Found.entrySet().iterator().next();
            return new HashMap<>() {{
                put(firstEntry.getKey(), firstEntry.getValue());
            }};
        }

        public boolean matchesSerial(Inet4Address address, ServiceEvent event, String matchSerial) {
            String infoUrl = "http://" + address.getHostAddress() + ":" + event.getInfo().getPort() + INFO_URL;
            try {
                String serialNumber = simpleHttpClient.get(infoUrl);
                return matchSerial.equals(serialNumber);
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void serviceAdded(ServiceEvent event) {
            logWriter.log("Service added: " + event.getInfo());
        }

        @Override
        public void serviceRemoved(ServiceEvent event) {
            logWriter.log("Service removed: " + event.getInfo());
        }

        @Override
        public void serviceResolved(ServiceEvent event) {
            logWriter.log("Service resolved: " + event.getInfo());

            if (matchSerial != null && !matchSerial.isEmpty()) {
                if (!matchesSerial(event.getInfo().getInet4Addresses()[0], event, matchSerial)) {
                    return; // We ignore the discovery if the serial isn't matching
                }
            }
            Arrays.stream(event.getInfo().getInetAddresses())
                    .forEach(inetAddress -> digitalPapersDiscovered.put(inetAddress, event));

            Inet4Address[] ipv4Addresses = event.getInfo().getInet4Addresses();
            if (ipv4Addresses != null && ipv4Addresses.length > 0) {
                ipv4Found.put(ipv4Addresses[0], event);
            }
        }
    }

    public static String findAddress(DeviceInfoStore deviceInfoStore, LogWriter logWriter, String address, String serial) throws IOException, InterruptedException {
        String addr;
        if (address != null) {
            addr = address;
        } else {
            // Before trying autoconfig, we can try loading the last ip
            String lastIp = deviceInfoStore.retrieveLastIp();
            if (lastIp != null && new PingCommand().ping(lastIp)) {
                addr = lastIp;
            } else {
                addr = new FindDigitalPaper(logWriter, new CheckedHttpClient(UncheckedHttpClient.insecure()), serial).findOneIpv4();
            }
        }
        if (addr == null || addr.isEmpty()) throw new IllegalStateException("No device found or reachable.");
        // We store the last address
        deviceInfoStore.storeLastIp(addr);

        // We test if the zeroconf digitalpaper.local is setup
        try {
            String zeroconfIp = new PingCommand().pingAndResolve(FindDigitalPaper.ZEROCONF_HOST);
            if (addr.equals(zeroconfIp)) return FindDigitalPaper.ZEROCONF_HOST;
        } catch (Exception ignored) {}
        return addr;
    }
}
