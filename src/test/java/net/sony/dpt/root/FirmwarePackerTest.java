package net.sony.dpt.root;

import net.sony.util.ByteUtils;
import net.sony.util.CryptographyUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

public class FirmwarePackerTest {

    // Firmware files are 200MB and can't be included
    private final static String TEST_FIRMWARE_LOCATION = "1.6.50.14130/FwUpdater.pkg";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private InputStream firmware() {
        return this.getClass().getClassLoader().getResourceAsStream(TEST_FIRMWARE_LOCATION);
    }

    @Test
    public void canLoadKeyPair() throws IOException {
        KeyPair keyPair = new FirmwarePacker(
                new CryptographyUtils(), System.out::println
        ).unpackKey();

        assertThat(keyPair, is(not(nullValue())));
        assertThat(keyPair.getPrivate(), is(not(nullValue())));
        assertThat(keyPair.getPublic(), is(not(nullValue())));
    }

    @Test
    public void validateHeader() throws IOException {
        InputStream firmware = firmware();
        Assume.assumeTrue(firmware != null);

        FirmwarePacker firmwarePacker = new FirmwarePacker(
                new CryptographyUtils(), System.out::println
        );

        byte[] firmareBytes = firmwarePacker.loadRawFirmware(firmware);
        assertTrue(firmwarePacker.checkHeader(firmareBytes));
    }

    @Test
    public void validateWrap() throws IOException {
        InputStream firmware = firmware();
        Assume.assumeTrue(firmware != null);

        FirmwarePacker firmwarePacker = new FirmwarePacker(
                new CryptographyUtils(), System.out::println
        );

        byte[] firmareBytes = firmwarePacker.loadRawFirmware(firmware);
        PkgWrap pkgWrap = firmwarePacker.wrap(firmareBytes);

        assertThat(pkgWrap.header(), is("DPUP"));
        assertThat(pkgWrap.offsetData(), is(568));
        assertThat(pkgWrap.dataSize(), is(215264816));
        assertThat(pkgWrap.dataKeyEncryptedSize(), is(256));
        assertThat(pkgWrap.animationHeaderSize(), is(268));
        assertThat(pkgWrap.animationDataSize(), is(644593));
        assertThat(pkgWrap.animationSigSize(), is(256));
    }

    @Test
    public void verifySignature() throws IOException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        InputStream firmware = firmware();
        Assume.assumeTrue(firmware != null);

        CryptographyUtils cryptographyUtils = new CryptographyUtils();
        FirmwarePacker firmwarePacker = new FirmwarePacker(
                cryptographyUtils, System.out::println
        );

        byte[] firmareBytes = firmwarePacker.loadRawFirmware(firmware);
        PkgWrap pkgWrap = firmwarePacker.wrap(firmareBytes);

        assertTrue(cryptographyUtils.verifySignature(pkgWrap.getEncryptedData(), pkgWrap.getSignature(), firmwarePacker.unpackKey().getPublic()));
    }

    @Test
    public void verifySignatureDecryption() throws IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, BadPaddingException, IllegalBlockSizeException {
        InputStream firmware = firmware();
        Assume.assumeTrue(firmware != null);
        CryptographyUtils cryptographyUtils = new CryptographyUtils();
        FirmwarePacker firmwarePacker = new FirmwarePacker(cryptographyUtils, System.out::println);

        byte[] firmareBytes = firmwarePacker.loadRawFirmware(firmware);
        PkgWrap pkgWrap = firmwarePacker.wrap(firmareBytes);

        byte[] decryptedDataKey = cryptographyUtils.decryptRsa(firmwarePacker.unpackKey().getPrivate(), pkgWrap.getEncryptedDataKey());
        assertThat(ByteUtils.bytesToHex(decryptedDataKey), is("35613538316231613937373566373433643637323761666666663635393134393161336237633437353633626334646665323062323165393932373263633234"));
    }

    @Test
    public void verifyUnpack() throws NoSuchPaddingException, SignatureException, NoSuchAlgorithmException, IOException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException, InvalidKeyException {
        InputStream firmware = firmware();
        Assume.assumeTrue(firmware != null);
        CryptographyUtils cryptographyUtils = new CryptographyUtils();
        FirmwarePacker firmwarePacker = new FirmwarePacker(cryptographyUtils, System.out::println);

        Path targetData = temporaryFolder.getRoot().toPath().resolve("decryptedData.tar.gz");
        Path targetAnimation = temporaryFolder.getRoot().toPath().resolve("decryptedAnimation.tar.gz");

        firmwarePacker.unpack(firmware, targetData, targetAnimation);
        assertThat(Files.size(targetData), is(215264813L));
        assertThat(Files.size(targetAnimation), is(644593L));
    }

    @Test
    public void verifyPack() throws Exception {
        InputStream firmware = firmware();
        Assume.assumeTrue(firmware != null);
        CryptographyUtils cryptographyUtils = new CryptographyUtils();
        FirmwarePacker firmwarePacker = new FirmwarePacker(cryptographyUtils, System.out::println);

        Path targetData = temporaryFolder.getRoot().toPath().resolve("decryptedData.tar.gz");
        Path targetAnimation = temporaryFolder.getRoot().toPath().resolve("decryptedAnimation.tar.gz");

        firmwarePacker.unpack(firmware, targetData, targetAnimation);
        assertThat(Files.size(targetData), is(215264813L));
        assertThat(Files.size(targetAnimation), is(644593L));

        Path targetFirmware = temporaryFolder.getRoot().toPath().resolve("encryptedFirmware.tar.gz");

        firmwarePacker.pack(Files.newInputStream(targetData), Files.newInputStream(targetAnimation), targetFirmware);

        // Since some part of the packing process is random, we verify by unpacking again
        firmwarePacker.unpack(Files.newInputStream(targetFirmware), targetData, targetAnimation);
        assertThat(Files.size(targetData), is(215264813L));
        assertThat(Files.size(targetAnimation), is(644593L));
    }

    // The hack at https://github.com/HappyZ/dpt-tools/blob/master/fw_updater_packer_by_shankerzhiwu/pkg_example/hack_basics/Makefile
    // makes the injection in the IV
    // dd if=$1 bs=$(($DATA_OFFSET)) skip=1 2>/dev/null | head -c $(($BODY_SIZE)) | i<-- that's where it uses "55 -none[...]" | tar -xz -C $2
    @Test
    public void decryptBase() throws IOException, NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, SignatureException, InvalidAlgorithmParameterException {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("root/FactoryReset.pkg");
        CryptographyUtils cryptographyUtils = new CryptographyUtils();
        FirmwarePacker firmwarePacker = new FirmwarePacker(cryptographyUtils, System.out::println);

        Path targetData = temporaryFolder.getRoot().toPath().resolve("decryptedData.tar.gz");
        firmwarePacker.unpack(inputStream, targetData, null);
        assertThat(Files.size(targetData), is(232975L));
    }

    @Test
    public void decryptPremadeHack() throws IOException {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("hackedFw/fw.pkg");

        Path targetData = temporaryFolder.getRoot().toPath().resolve("decryptedData.tar.gz");
        RootPacker rootPacker = new RootPacker();
        rootPacker.unpackRootPackage(inputStream, targetData);
        assertThat(Files.size(targetData), is(185275L));
    }

    @Test
    public void validateIsoHack() throws IOException, URISyntaxException {
        InputStream expectedStream = this.getClass().getClassLoader().getResourceAsStream("hackedFw/fw.pkg");
        assert expectedStream != null;
        // TODO: untar to analyze userid - make sure "--numeric-owner isn't important"
        byte[] expectedBytes = IOUtils.toByteArray(expectedStream);

        RootPacker rootPacker = new RootPacker();
        byte[] actualBytes = rootPacker.createStandardRootPackage();

        Path expectedDataTar = temporaryFolder.getRoot().toPath().resolve("expectedDataTar.tar.gz");
        Path actualDataTar = temporaryFolder.getRoot().toPath().resolve("actualDataTar.tar.gz");

        rootPacker.unpackRootPackage(new ByteArrayInputStream(expectedBytes), expectedDataTar);
        rootPacker.unpackRootPackage(new ByteArrayInputStream(actualBytes), actualDataTar);
    }

}
