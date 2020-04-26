package net.sony.dpt.root;

import net.sony.util.ByteUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;

public class PkgWrap {

    private final ByteBuffer wrap;

    private final String header;
    private final int offsetData;
    private final int dataSize;
    private final int sigSize;
    private final byte[] signature;

    private final int dataKeyEncryptedSize;
    private final byte[] encryptedDataKey;

    private final byte[] iv;

    private final byte[] encryptedData;

    private int animationHeaderSize;
    private int animationDataSize;
    private int animationSigSize;
    private byte[] animationSignature;
    private byte[] animationData;


    public PkgWrap(final byte[] content) {
        wrap = ByteBuffer.wrap(content);
        wrap.order(ByteOrder.LITTLE_ENDIAN);

        header = new String(ArrayUtils.subarray(content, 0, 4), StandardCharsets.US_ASCII);
        wrap.clear();
        wrap.getInt();

        // Reading data block size
        offsetData = wrap.getInt();
        dataSize = wrap.getInt();

        wrap.position(wrap.position() + 4); // byte 13--16:        nothing

        // Extract signature
        sigSize = wrap.getInt();
        signature = new byte[sigSize];
        wrap.get(signature, 0, sigSize);

        // Get encrypted data aes key
        dataKeyEncryptedSize = wrap.getInt();
        encryptedDataKey = new byte[dataKeyEncryptedSize];
        wrap.get(encryptedDataKey, 0, dataKeyEncryptedSize);

        // Extract 32 byte intial vector
        iv = new byte[32];
        wrap.get(iv, 0, 32);

        // Data
        encryptedData = new byte[dataSize];
        wrap.position(offsetData);
        wrap.get(encryptedData, 0, dataSize);

        // Checking if animation data follows
        if (wrap.position() < wrap.limit()) {
            animationHeaderSize = wrap.getInt();
            if (animationHeaderSize <= 0) return;
            // Found animation block, getting animation data size
            animationDataSize = wrap.getInt();

            // Get animation signature
            animationSigSize = wrap.getInt();
            animationSignature = new byte[animationSigSize];
            wrap.get(animationSignature, 0, animationSigSize);

            // Animation data
            animationData = new byte[animationDataSize];
            wrap.get(animationData, 0, animationDataSize);
        }

    }

    public String header() {

        return header;
    }

    public int offsetData() {
        return offsetData;
    }

    public int dataSize() {
        return dataSize;
    }

    public int dataKeyEncryptedSize() {
        return dataKeyEncryptedSize;
    }

    public int animationHeaderSize() {
        return animationHeaderSize;
    }

    public int animationDataSize() {
        return animationDataSize;
    }

    public int animationSigSize() {
        return animationSigSize;
    }

    public byte[] getSignature() {
        return signature;
    }

    public byte[] getEncryptedDataKey() {
        return encryptedDataKey;
    }

    // The IV is actually an hex-encoded binary string
    public byte[] getIv() {
        return iv;
    }

    // This will return a binary 16-bytes IV for use with the Java Cipher
    public byte[] getBinaryIv() {
        return ByteUtils.hexToByte(new String(iv, StandardCharsets.US_ASCII));
    }

    public byte[] getEncryptedData() {
        return encryptedData;
    }

    public byte[] getAnimationSignature() {
        return animationSignature;
    }

    public byte[] getAnimationData() {
        return animationData;
    }

}
