package net.sony.dpt.root;

import net.sony.util.ByteUtils;
import net.sony.util.CryptographyUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

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
        assumeThat(firmware, is(notNullValue()));

        FirmwarePacker firmwarePacker = new FirmwarePacker(
                new CryptographyUtils(), System.out::println
        );

        byte[] firmareBytes = firmwarePacker.loadRawFirmware(firmware);
        assertTrue(firmwarePacker.checkHeader(firmareBytes));
    }

    @Test
    public void validateWrap() throws IOException {
        InputStream firmware = firmware();
        assumeThat(firmware, is(notNullValue()));

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
        assumeThat(firmware, is(notNullValue()));

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
        assumeThat(firmware, is(notNullValue()));
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
        assumeThat(firmware, is(notNullValue()));
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
        assumeThat(firmware, is(notNullValue()));
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
    public void decryptHack() throws IOException, IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("hackedFw/fw.pkg");
        CryptographyUtils cryptographyUtils = new CryptographyUtils();
        FirmwarePacker firmwarePacker = new FirmwarePacker(cryptographyUtils, System.out::println);

        PkgWrap wrap = firmwarePacker.wrap(firmwarePacker.loadRawFirmware(inputStream));
        KeyPair unpackKey = firmwarePacker.unpackKey();

        byte[] decryptedKey = cryptographyUtils.decryptRsa(unpackKey.getPrivate(), wrap.getEncryptedDataKey());
        System.out.println(new String(decryptedKey, StandardCharsets.UTF_8));

        byte[] binaryKey = ByteUtils.hexToByte(new String(decryptedKey, StandardCharsets.US_ASCII).trim());
        ByteBuffer buffer = ByteBuffer.wrap(binaryKey);
        buffer.order(ByteOrder.BIG_ENDIAN);
        while (buffer.position() < buffer.limit()) System.out.println(buffer.getChar());
    }

}
