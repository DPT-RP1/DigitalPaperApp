package net.sony.dpt.root;

import com.fazecast.jSerialComm.SerialPort;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Scanner;

import static org.junit.Assert.assertTrue;

public class DiagnosticTest {

    private DiagnosticManager diagnosticManager;
    private SerialPort serialPort;

    public void assumeEngaged() {
        diagnosticManager = new DiagnosticManager(message -> {
            System.out.println(message);
            System.out.flush();
        }, () -> {
            Scanner scanner = new Scanner(System.in);
            return scanner.nextLine();
        });

        boolean isEngaged = diagnosticManager.isDiagnosticEngaged();

        Assume.assumeTrue(isEngaged);
    }

    @Before
    public void setup() {
        assumeEngaged();
    }

    @Test
    public void canOpenTTY() {
        serialPort = diagnosticManager.findDPTTty(false);
        assertTrue(diagnosticManager.open(serialPort));
        assertTrue(serialPort.isOpen());
    }


    @Test
    public void canLoginTTY() throws IOException, InterruptedException {
        serialPort = diagnosticManager.findDPTTty(false);
        diagnosticManager.open(serialPort);

        assertTrue(diagnosticManager.login(serialPort));

    }

    @Test
    public void canLogoutTTY() throws IOException, InterruptedException {
        serialPort = diagnosticManager.findDPTTty(false);
        diagnosticManager.open(serialPort);
        assertTrue(diagnosticManager.logout(serialPort));
    }

    @After
    public void tearDown() {
        if (serialPort != null) serialPort.closePort();
    }
}
