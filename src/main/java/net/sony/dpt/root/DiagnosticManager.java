package net.sony.dpt.root;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import jnr.ffi.annotations.In;
import net.sony.util.InputReader;
import net.sony.util.LogWriter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.fazecast.jSerialComm.SerialPort.LISTENING_EVENT_DATA_RECEIVED;

public class DiagnosticManager {
    private static final String SERIAL_PORT_NAME = "FPX-1010";

    private final LogWriter logWriter;
    private final InputReader inputReader;

    public DiagnosticManager(final LogWriter logWriter, final InputReader inputReader) {
        this.logWriter = logWriter;
        this.inputReader = inputReader;
    }

    public SerialPort findDPTTty(boolean plugPrompt) {
        logWriter.log("Scanning for available serial ports...");
        for(SerialPort port: SerialPort.getCommPorts()){
            if (SERIAL_PORT_NAME.equals(port.getDescriptivePortName())) {
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

    public boolean open(SerialPort serialPort) {
        serialPort.closePort();
        serialPort.setBaudRate(115200);
        boolean open = serialPort.openPort(1000);
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
    private static final String ROOT_PROMPT = "root@FPX-1010:~#";
    private static final String ROOT_USER = "root";
    private static final String ROOT_PASSWORD = "12345";

    private int write(String content, SerialPort serialPort) {
        OutputStream outputStream = serialPort.getOutputStream();
        try {
            outputStream.write(content.getBytes());
            outputStream.flush();
        } catch (IOException e) {
            return 0;
        }
        return content.length();

    }

    public boolean login(final SerialPort serialPort) throws IOException, InterruptedException {
        if (!serialPort.isOpen()) throw new IllegalStateException("Please open the serial port before logging in");

        InputStream inputStream = serialPort.getInputStream();

        Thread.sleep(500);

        if (inputStream.available() == 0) {
            // We wake up the port
            write("\n", serialPort);
            return false;
        }

        AtomicBoolean loggedIn = new AtomicBoolean(false);

        serialPort.addDataListener(new SerialPortDataListener() {
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
                        write(ROOT_USER + "\n", serialPort);
                    }

                    if (responseString.contains(PASSWORD_PROMPT)) {
                        write((ROOT_PASSWORD + "\n"), serialPort);
                    }
                    loggedIn.set(responseString.contains(ROOT_PROMPT));
                }

            }
        });

        int attempt = 10;
        while(!loggedIn.get()) {
            if (attempt == 0) return false;
            attempt -= 1;
            Thread.sleep(500);
        };

        return true;
    }

    public boolean logout(SerialPort serialPort) throws IOException, InterruptedException {
        // We go through the login routine first in case the serial is stuck somewhere
        login(serialPort);
        OutputStream outputStream = serialPort.getOutputStream();
        outputStream.write("reboot\n".getBytes());
        outputStream.flush();

        return true;
    }
}
