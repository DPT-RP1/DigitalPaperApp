package net.sony.dpt.root;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortMessageListener;
import net.sony.util.InputReader;
import net.sony.util.LogWriter;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.fazecast.jSerialComm.SerialPort.LISTENING_EVENT_DATA_RECEIVED;
import static com.fazecast.jSerialComm.SerialPort.TIMEOUT_NONBLOCKING;

public class DiagnosticManager {
    private static final String SERIAL_PORT_NAME = "FPX-1010";

    public static final Path SYSTEM_BLOCK_DEVICE = Path.of("/dev/mmcblk0p9");
    public static final Path SD_BLOCK_DEVICE = Path.of("/dev/mmcblk0p16");

    public static final Path SYSTEM_STD_MOUNT_POINT = Path.of("/system");
    public static final Path SD_STD_MOUNT_POINT = Path.of("/mnt/sd");

    private static final String NO_SUCH_FILE = "No such file or directory";

    private final LogWriter logWriter;
    private final InputReader inputReader;

    // Files are notoriously difficult to fetch on serial (when do we stop etc)
    // It is usual to base64 them on one line first and get that.
    private final Base64.Decoder base64Decoder;

    private SerialPort serialPort;

    public DiagnosticManager(final LogWriter logWriter, final InputReader inputReader) {
        this.logWriter = logWriter;
        this.inputReader = inputReader;
        this.base64Decoder = Base64.getDecoder();
        findDPTTty(true);
    }

    public SerialPort findDPTTty(boolean plugPrompt) {
        logWriter.log("Scanning for available serial ports...");
        for(SerialPort port: SerialPort.getCommPorts()){
            if (SERIAL_PORT_NAME.equals(port.getDescriptivePortName())) {
                serialPort = port;
                return port;
            }
        }
        if (plugPrompt) {
            logWriter.log("No tty device found, did you: ");
            logWriter.log("\t1. Root your device");
            logWriter.log("\t2. Turn off completely your DPT (long-press power), press home then power for a little while, saw black squares on the device ");
            logWriter.log("\t3. Plug your device to a USB port on this computer");
            logWriter.log("\tIf so, press y, otherwise n");
            String response = inputReader.read();
            if (response.contains("y")) findDPTTty(false);
        }
        return null;

    }

    public boolean isDiagnosticEngaged() {
        return findDPTTty(false) != null;
    }

    public boolean open() {
        if (serialPort.isOpen()) return true;

        serialPort.setBaudRate(115200);
        boolean open = serialPort.openPort(2000);
        if (!open) {
            logWriter.log("Could not open the tty port /dev/" + serialPort.getSystemPortName());
            logWriter.log("This can be due to permission issues, make sure the device tty is readable to your current user");
            logWriter.log("1. Your system must have a dialout group (sudo newgrp dialout) ");
            logWriter.log("2. Your user must be in the dialout group (sudo adduser $USER dialout)");
            logWriter.log("3. You must reboot your computer after this to make the changes take effect");
            logWriter.log("You can test interactive serial commands using minicom on linux");
        }
        return open;
    }

    private static final String LOGIN_PROMPT = "FPX-1010 login:";
    private static final String PASSWORD_PROMPT = "Password:";
    private static final String ROOT_PROMPT = "root@FPX-1010:";
    private static final String ROOT_USER = "root";
    private static final String ROOT_PASSWORD = "12345";

    public boolean login() throws IOException, InterruptedException {
        if (!serialPort.openPort()) throw new IllegalStateException("Please open the serial port before logging in");

        InputStream inputStream = serialPort.getInputStream();

        Thread.sleep(500);

        if (inputStream.available() == 0) {
            // We wake up the port
            write("\n");
            Thread.sleep(500);
        }

        AtomicBoolean loggedIn = new AtomicBoolean(false);
        SerialPortDataListener dataListener = new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return LISTENING_EVENT_DATA_RECEIVED;
            }

            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() == LISTENING_EVENT_DATA_RECEIVED) {
                    String responseString = new String(event.getReceivedData());
                    logWriter.log(responseString);

                    if (responseString.contains(LOGIN_PROMPT)) {
                        write(ROOT_USER + "\n");
                    }

                    if (responseString.contains(PASSWORD_PROMPT)) {
                        write((ROOT_PASSWORD + "\n"));
                    }
                    loggedIn.set(responseString.contains(ROOT_PROMPT));
                }

            }
        };
        serialPort.addDataListener(dataListener);

        final AtomicInteger attempt = new AtomicInteger(10);

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            if (loggedIn.get() || attempt.get() <= 0) executor.shutdownNow();
            attempt.decrementAndGet();
        }, 0, 1000, TimeUnit.MILLISECONDS);
        executor.awaitTermination(30, TimeUnit.SECONDS);
        serialPort.removeDataListener();
        return attempt.get() > 0;
    }

    public boolean logout() throws IOException, InterruptedException {
        // We go through the login routine first in case the serial is stuck somewhere
        login();
        OutputStream outputStream = serialPort.getOutputStream();
        outputStream.write("reboot\n".getBytes());
        outputStream.flush();

        return true;
    }

    public byte[] fetchFile(final Path path) throws IOException, InterruptedException {
        String fetchCommand = "cat " + path.toString() + " | busybox base64\n";

        logWriter.log("Preparing to send " + fetchCommand);

        StringBuilder base64File = new StringBuilder();
        AtomicBoolean fullyLoaded = new AtomicBoolean(false);

        if (!login()) throw new IllegalStateException("Impossible to login in diag mode");

        // We flush the input stream so that we read ONLY the file content
        flushSerialPort();

        SerialPortDataListener fileDataListener = new SerialPortDataListener() {

            @Override
            public int getListeningEvents() { return LISTENING_EVENT_DATA_RECEIVED; }

            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() == LISTENING_EVENT_DATA_RECEIVED) {
                    if (!fullyLoaded.get()) {
                        // We need to stop once we reach the end of file (new prompt)
                        String dataStringView = new String(event.getReceivedData());
                        base64File.append(dataStringView.replaceAll("\r", "").replaceAll("\n", ""));
                        fullyLoaded.set(base64File.toString().contains(ROOT_PROMPT));
                    }
                }
            }
        };

        serialPort.removeDataListener();
        serialPort.addDataListener(fileDataListener);
        write(fetchCommand);

        logWriter.log("Waiting for the file transfer to complete... this will time out after 2 minutes...");
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            if (fullyLoaded.get()) {
                executor.shutdownNow();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        executor.awaitTermination(2, TimeUnit.MINUTES);
        serialPort.removeDataListener();
        if (!fullyLoaded.get()) throw new IllegalStateException("The file could not be downloaded");

        String base64String = base64File.toString();
        base64String = base64String.substring(base64String.indexOf(fetchCommand) + fetchCommand.length(), base64String.indexOf(ROOT_PROMPT));
        byte[] fileContent = base64Decoder.decode(base64String);
        logWriter.log("Files loaded (MD5: " + DigestUtils.md5Hex(fileContent) + ", Size: " + fileContent.length + ")");
        return fileContent;
    }

    public void setSerialPort(SerialPort serialPort) {
        this.serialPort = serialPort;
    }

    public void mount(final Path device, final Path mountPoint) throws IOException, InterruptedException {
        if (!login()) throw new IllegalStateException("Impossible to login in diag mode");
        final Map<String, String> alreadyMounted = mountPoints();
        if (alreadyMounted.containsKey(device.toString()) && alreadyMounted.get(device.toString()).equals(mountPoint.toString())) return;

        flushSerialPort();

        // 1. Test if mount point exist
        String command = "ls -l " + mountPoint.toString();
        String response = sendCommand(command);

        // 2. Unmount if so
        if (!response.isEmpty() && !response.contains(NO_SUCH_FILE)) {
            command = "unmount " + mountPoint;
            sendCommand(command);
        }

        // 3. Create mount point if doesn't exist
        if (response.contains(NO_SUCH_FILE)) {
            command = "mkdir -p " + mountPoint;
            sendCommand(command);
        }

        // 4. Mount
        command = "mount " + device + " " + mountPoint;
        response = sendCommand(command);
        if (!response.isEmpty()) logWriter.log(response);
    }

    public void automount() throws IOException, InterruptedException {
        mount(SYSTEM_BLOCK_DEVICE, SYSTEM_STD_MOUNT_POINT);
        mount(SD_BLOCK_DEVICE, SD_STD_MOUNT_POINT);
    }

    /**
     * Slow, byte by byte, blocking line-read, to use when short responses are expected.
     * This helps figure out when to stop without having a full async Message listener.
     * @return The line read, or String.EMPTY otherwise.
     */
    private String readLine() throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while((c = serialPort.getInputStream().read()) != '\n') {
            line.append((char) c);
            if (line.toString().contains(ROOT_PROMPT)) break;
        }
        return line.toString().trim();
    }

    public void flushSerialPort() throws IOException {
        serialPort.getOutputStream().flush();
        if (serialPort.getInputStream().available() > 0) {
            logWriter.log(new String(serialPort.getInputStream().readNBytes(serialPort.getInputStream().available())));
        }
    }

    private void write(final String content) {
        OutputStream outputStream = serialPort.getOutputStream();
        try {
            outputStream.write(content.getBytes());
            outputStream.flush();
        } catch (IOException ignored) { }
    }

    private void writeLine(final String content) {
        write(content + "\n");
    }

    public String sendCommand(final String command) throws IOException {
        writeLine(command);
        StringBuilder stringBuilder = new StringBuilder();

        int oldReadTimeout = serialPort.getReadTimeout();
        int oldWriteTimeout = serialPort.getWriteTimeout();
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);


        String response = "";
        while (!response.contains(ROOT_PROMPT)) {
            response = readLine();
            if (!response.equals(command) && !response.contains(ROOT_PROMPT)) {
                stringBuilder.append(response).append("\n");
            }
        }
        flushSerialPort();
        // Reset timeout mode
        serialPort.setComPortTimeouts(TIMEOUT_NONBLOCKING, oldReadTimeout, oldWriteTimeout);
        return stringBuilder.toString().trim();
    }

    public Map<String, String> mountPoints() throws IOException, InterruptedException {

        login();

        Map<String, String> result = new HashMap<>();

        String mounts = sendCommand("cat /proc/mounts");
        String[] mountsArray = mounts.split("\n");
        for (String mountLine : mountsArray) {
            if (mountLine.startsWith("/")) {
                String[] descriptors = mountLine.split(" ");
                result.put(descriptors[0], descriptors[1]);
            }
        }
        return result;
    }

    public void close() {
        serialPort.closePort();
    }
}
