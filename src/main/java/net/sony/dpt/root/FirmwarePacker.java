package net.sony.dpt.root;

import net.sony.util.ByteUtils;
import net.sony.util.CryptographyUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.Objects;

/**
 * PKG FILE FORMAT
 * byte 1--4:          DPUP - file recognizer
 * byte 5--8:          data offset location A
 * byte 9--12:         total data content size D
 * byte 13--16:        nothing
 * byte 17--20:        sigfile size B
 * byte 21--(20+B):    sigfile data
 * byte (21+(-B%16)%16)--(25+(-B%16)%16):  encrypted data aes key size (BE) C
 * byte (+1)--(+C):    encrypted data aes key bytes
 * byte (+1)--(+32):   initial vector  --- up till now bytes shall equal to A
 * byte (A+1)--(A+D):  encrypted data
 * byte (+1)--(+4):    animation header size E
 * byte (+1)--(+4):    animation data size F
 * byte (+1)--(+4):    animation sigfile size G
 * byte (+1)--(+(-G%16)%16): animation sigfile --- here bytes shall be A+D+E
 * byte (A+D+E+1)--(A+D+E+F): animation data
 * 
 * zipped data format
 * FwUpdater
 * |- boot.img
 * |- boot.img.md5
 * |- system.img
 * |- system.img.md5
 * |- eufwupdater.sh
 * |- ...
 */
public class FirmwarePacker {

    private static final String UNPACK_KEY_RESOURCE_LOCATION_PRIVATE = "root/unpack_key.private";
    private static final String UNPACK_KEY_RESOURCE_LOCATION_PUBLIC = "root/unpack_key.public";

    private static final String DPUP = "DPUP";

    private final CryptographyUtils cryptographyUtils;

    public FirmwarePacker(final CryptographyUtils cryptographyUtils) {
        this.cryptographyUtils = cryptographyUtils;
    }

    KeyPair unpackKey() throws IOException {
        String privateKeyPem = IOUtils.toString(
                Objects.requireNonNull(FirmwarePacker.class.getClassLoader().getResourceAsStream(UNPACK_KEY_RESOURCE_LOCATION_PRIVATE)),
                StandardCharsets.UTF_8
        );

        String publicKeyPem = IOUtils.toString(
                Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(UNPACK_KEY_RESOURCE_LOCATION_PUBLIC)),
                StandardCharsets.UTF_8
        );

        return cryptographyUtils.readPkcs1KeyPair(privateKeyPem, publicKeyPem);
    }

    public byte[] loadRawFirmware(final InputStream source) throws IOException {
        return IOUtils.toByteArray(source);
    }

    public boolean checkHeader(final byte[] pkg) {
        String header = new String(
                ArrayUtils.subarray(pkg, 0, 4),
                StandardCharsets.US_ASCII
        );
        return (DPUP.equals(header));
    }

    public PkgWrap wrap(final byte[] pkg) {
        return new PkgWrap(pkg);
    }

    public boolean verifyDataSignature(PkgWrap wrap, PublicKey publicKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        // Verify data with signature
        byte[] encryptedData = wrap.getEncryptedData();
        byte[] signature = wrap.getSignature();

        return cryptographyUtils.verifySignature(encryptedData, signature, publicKey);
    }

    private byte[] decrypt(byte[] source) throws IOException, NoSuchAlgorithmException, InvalidKeyException, SignatureException, NoSuchPaddingException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
        KeyPair unpackKey = unpackKey();

        PkgWrap wrap = wrap(source);
        if (!verifyDataSignature(wrap, unpackKey.getPublic())) {
            throw new IllegalStateException("Impossible to verify firmware");
        }

        byte[] decryptedDataKey = cryptographyUtils.decryptRsa(unpackKey.getPrivate(), wrap.getEncryptedDataKey());

        // The decryptedDataKey is an hexadecimal representation, it should be instead a 256bit binary key
        return cryptographyUtils.decryptAES(wrap.getEncryptedData(), ByteUtils.hexToByte(new String(decryptedDataKey, StandardCharsets.US_ASCII)), wrap.getBinaryIv());
    }

    public void unpack(InputStream officialFirmware, Path targetDirectory) throws IOException, NoSuchPaddingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, SignatureException, InvalidKeyException {
        byte[] decryptedTarGz = decrypt(loadRawFirmware(officialFirmware));
        Files.write(targetDirectory, decryptedTarGz);
    }

}
