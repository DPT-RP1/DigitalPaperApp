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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.fazecast.jSerialComm.SerialPort.LISTENING_EVENT_DATA_RECEIVED;

public class DiagnosticManager {
    private static final String SERIAL_PORT_NAME = "FPX-1010";

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

    private void write(final String content) {
        OutputStream outputStream = serialPort.getOutputStream();
        try {
            outputStream.write(content.getBytes());
            outputStream.flush();
        } catch (IOException ignored) { }
    }

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

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        AtomicBoolean fullyLoaded = new AtomicBoolean(false);

        if (!login()) throw new IllegalStateException("Impossible to login in diag mode");

        // We flush the input stream so that we read ONLY the file content
        if (serialPort.getInputStream().available() > 0) {
            logWriter.log(new String(serialPort.getInputStream().readAllBytes()));
        }

        SerialPortDataListener fileDataListener = new SerialPortMessageListener() {
            @Override
            public byte[] getMessageDelimiter() { return "\r\n".getBytes(); }

            @Override
            public boolean delimiterIndicatesEndOfMessage() { return true; }

            @Override
            public int getListeningEvents() { return LISTENING_EVENT_DATA_RECEIVED; }

            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() == LISTENING_EVENT_DATA_RECEIVED) {
                    // We need to stop once we reach the end of file (new prompt)
                    String dataStringView = new String(event.getReceivedData());
                    if (!dataStringView.trim().contains(ROOT_PROMPT.trim()) && !dataStringView.trim().contains(fetchCommand.trim())) {

                        byte[] decoded = base64Decoder.decode(dataStringView.trim());
                        try { file.write(decoded); file.flush();} catch (IOException ignored) { }
                        fullyLoaded.set(true);
                    }
                }
            }
        };

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

        byte[] fileContent = file.toByteArray();
        logWriter.log("Files loaded (MD5: " + DigestUtils.md5Hex(fileContent) + ", Size: " + fileContent.length + ")");
        return fileContent;
    }

    public void setSerialPort(SerialPort serialPort) {
        this.serialPort = serialPort;
    }

    public void close() {
        serialPort.closePort();
    }
}
