package net.sony.dpt.root;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.junit.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Scanner;

import static net.sony.dpt.root.DiagnosticManager.*;
import static net.sony.dpt.root.DiagnosticManager.SD_BLOCK_DEVICE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

public class DiagnosticTest {

    private DiagnosticManager diagnosticManager;

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
        assertTrue(diagnosticManager.open());
    }


    @Test
    public void canLoginTTY() throws IOException, InterruptedException {
        diagnosticManager.open();
        assertTrue(diagnosticManager.login());

    }

    @Ignore("This reboots the device, inconvenient...")
    @Test
    public void canLogoutTTY() throws IOException, InterruptedException {
        diagnosticManager.open();
        assertTrue(diagnosticManager.logout());
    }

    @Test
    public void canFetchAsciiFile() throws IOException, InterruptedException {
        Path file = Path.of("/etc/fstab");

        byte[] fileContent = diagnosticManager.fetchFile(file);

        assertThat(new String(fileContent), is("# UNCONFIGURED FSTAB FOR BASE SYSTEM\n"));
    }


    @Test
    public void canFetchBinaryFile() throws IOException, InterruptedException {
        Path file = Path.of("/bin/su");

        String remoteMD5 = "ba5cc3feb1af892234de55697d5fd814";
        byte[] expected = IOUtils.toByteArray(Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream("diagnostic/original_su")));
        String localExpectedMD5 = DigestUtils.md5Hex(expected);

        byte[] fileContent = diagnosticManager.fetchFile(file);
        String actualReceivedMD5 = DigestUtils.md5Hex(fileContent);

        assertEquals(remoteMD5, localExpectedMD5);
        assertEquals(localExpectedMD5, actualReceivedMD5);

        assertThat(fileContent.length, is(27068));
        assertArrayEquals(expected, fileContent);
    }

    @Test
    public void canMountSd() throws IOException, InterruptedException {
        diagnosticManager.mount(SD_BLOCK_DEVICE, SD_STD_MOUNT_POINT);
        String mounts = diagnosticManager.sendCommand("cat /proc/mounts");

        assertTrue(mounts.contains(SD_BLOCK_DEVICE.toString()));
        assertTrue(mounts.contains(SD_STD_MOUNT_POINT.toString()));
    }

    @Test
    public void canMountSystem() throws IOException, InterruptedException {
        diagnosticManager.mount(SYSTEM_BLOCK_DEVICE, SYSTEM_STD_MOUNT_POINT);
        String mounts = diagnosticManager.sendCommand("cat /proc/mounts");

        assertTrue(mounts.contains(SYSTEM_BLOCK_DEVICE.toString()));
        assertTrue(mounts.contains(SYSTEM_STD_MOUNT_POINT.toString()));
    }

    @After
    public void tearDown() {
        diagnosticManager.close();
    }
}
