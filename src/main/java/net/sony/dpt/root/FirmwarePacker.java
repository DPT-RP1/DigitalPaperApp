package net.sony.dpt.root;

import net.sony.util.ByteUtils;
import net.sony.util.CryptographyUtils;
import net.sony.util.LogWriter;
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
    private final LogWriter logWriter;

    public FirmwarePacker(final CryptographyUtils cryptographyUtils, final LogWriter logWriter) {
        this.cryptographyUtils = cryptographyUtils;
        this.logWriter = logWriter;
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

    private byte[] encrypt(byte[] decryptedData, byte[] decryptedKey, byte[] binaryIv) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        return cryptographyUtils.encryptAES(decryptedData, decryptedKey, binaryIv);
    }

    private byte[] decrypt(byte[] encryptedData,
                           byte[] signature,
                           KeyPair unpackKey,
                           byte[] encryptedKey,
                           byte[] binaryIv) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, NoSuchPaddingException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
        if (!cryptographyUtils.verifySignature(
                encryptedData,
                signature,
                unpackKey.getPublic()
        )) {
            logWriter.log("Impossible to verify firmware, normal if self-signed");
        }

        byte[] decryptedDataKey = cryptographyUtils.decryptRsa(unpackKey.getPrivate(), encryptedKey);

        // The decryptedDataKey is an hexadecimal representation, it should be instead a 256bit binary key
        return cryptographyUtils.decryptAES(encryptedData, ByteUtils.hexToByte(new String(decryptedDataKey, StandardCharsets.US_ASCII)), binaryIv);
    }

    private byte[] decryptData(PkgWrap wrap, KeyPair unpackKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, NoSuchPaddingException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
        return decrypt(wrap.getEncryptedData(), wrap.getSignature(), unpackKey, wrap.getEncryptedDataKey(), wrap.getBinaryIv());
    }

    public void unpack(InputStream firmware, Path targetDataFile, Path targetAnimationFile) throws IOException, NoSuchPaddingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, SignatureException, InvalidKeyException {
        KeyPair unpackKey = unpackKey();

        PkgWrap wrap = wrap(loadRawFirmware(firmware));

        byte[] decryptedDataTarGz = decryptData(wrap, unpackKey);
        Files.write(targetDataFile, decryptedDataTarGz);

        if (!cryptographyUtils.verifySignature(
                wrap.getAnimationData(),
                wrap.getAnimationSignature(),
                unpackKey.getPublic()
        )) {
            logWriter.log("Impossible to verify firmware animation, this is normal if self-signed");
        }
        Files.write(targetAnimationFile, wrap.getAnimationData());
    }

    public void pack(InputStream decryptedDataTarGz, InputStream decryptedAnimationTarGz, Path targetEncryptedFirmware) throws Exception {
        KeyPair unpackKey = unpackKey();

        byte[] decryptedDataBytes = IOUtils.toByteArray(decryptedDataTarGz);
        byte[] decryptedAnimationBytes = IOUtils.toByteArray(decryptedAnimationTarGz);

        byte[] iv = cryptographyUtils.generateNonce(16);
        byte[] decryptedKey = cryptographyUtils.generateRandomAESKey();

        byte[] encryptedDataBytes = encrypt(decryptedDataBytes, decryptedKey, iv);

        byte[] hexDecryptedKey = ByteUtils.bytesToHex(decryptedKey).toLowerCase().getBytes(StandardCharsets.US_ASCII);
        byte[] hexIv = ByteUtils.bytesToHex(iv).toLowerCase().getBytes(StandardCharsets.US_ASCII);
        byte[] encryptedKey = cryptographyUtils.encryptRsa(unpackKey.getPublic(), hexDecryptedKey);

        PkgWrap pkgWrap = new PkgWrap(
            DPUP,
            cryptographyUtils.signSHA256RSA(encryptedDataBytes, unpackKey.getPrivate()),
            encryptedKey,
            hexIv,
            encryptedDataBytes,
            cryptographyUtils.signSHA256RSA(decryptedAnimationBytes, unpackKey.getPrivate()),
            decryptedAnimationBytes
        );
        Files.write(targetEncryptedFirmware, pkgWrap.getBytes());
    }

}
