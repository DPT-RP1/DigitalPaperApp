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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;

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
                new CryptographyUtils()
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
                new CryptographyUtils()
        );

        byte[] firmareBytes = firmwarePacker.loadRawFirmware(firmware);
        assertTrue(firmwarePacker.checkHeader(firmareBytes));
    }

    @Test
    public void validateWrap() throws IOException {
        InputStream firmware = firmware();
        assumeThat(firmware, is(notNullValue()));

        FirmwarePacker firmwarePacker = new FirmwarePacker(
                new CryptographyUtils()
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

        FirmwarePacker firmwarePacker = new FirmwarePacker(
                new CryptographyUtils()
        );

        byte[] firmareBytes = firmwarePacker.loadRawFirmware(firmware);
        PkgWrap pkgWrap = firmwarePacker.wrap(firmareBytes);

        assertTrue(firmwarePacker.verifyDataSignature(pkgWrap, firmwarePacker.unpackKey().getPublic()));
    }

    @Test
    public void verifySignatureDecryption() throws IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, BadPaddingException, IllegalBlockSizeException {
        InputStream firmware = firmware();
        assumeThat(firmware, is(notNullValue()));
        CryptographyUtils cryptographyUtils = new CryptographyUtils();
        FirmwarePacker firmwarePacker = new FirmwarePacker(cryptographyUtils);

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
        FirmwarePacker firmwarePacker = new FirmwarePacker(cryptographyUtils);

        Path target = temporaryFolder.getRoot().toPath().resolve("decrypted.tar.gz");
        firmwarePacker.unpack(firmware, target);
        assertThat(Files.size(target), is(215264813L));
    }
}
