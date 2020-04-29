package net.sony.dpt.root;

import net.sony.util.ByteUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static java.util.Objects.deepEquals;

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

    public PkgWrap(String header, byte[] signature, byte[] encryptedDataKey, byte[] iv, byte[] encryptedData, byte[] animationSignature, byte[] animationData) {
        this.header = header;
        this.signature = signature;
        this.encryptedDataKey = encryptedDataKey;
        this.iv = iv;
        this.encryptedData = encryptedData;
        this.animationSignature = animationSignature;
        this.animationData = animationData;

        dataSize = encryptedData.length;
        sigSize = signature.length;
        dataKeyEncryptedSize = encryptedDataKey.length;
        offsetData = 32 + dataKeyEncryptedSize + sigSize + 6 * 4 ;

        if (animationData != null) {
            animationDataSize = animationData.length;
            this.animationSigSize = animationSignature.length;
        }

        wrap = ByteBuffer.allocate(offsetData + dataSize + animationSigSize + animationDataSize + 12);
        wrap.order(ByteOrder.LITTLE_ENDIAN);

        wrap.clear();

        wrap.put(header.getBytes(StandardCharsets.US_ASCII), 0, 4);
        wrap.putInt(offsetData);
        wrap.putInt(dataSize);
        wrap.putInt(0);

        wrap.putInt(sigSize);
        wrap.put(signature, 0, sigSize);

        wrap.putInt(dataKeyEncryptedSize);
        wrap.put(encryptedDataKey, 0, dataKeyEncryptedSize);

        wrap.put(iv, 0, 32);

        if (wrap.position() != offsetData) throw new IllegalStateException("Wrong offset...");
        wrap.put(encryptedData, 0, dataSize);

        if (animationData != null) {
            this.animationData = animationData;
            this.animationSignature = animationSignature;
            wrap.putInt(4 * 3 + animationSigSize);
            wrap.putInt(animationDataSize);
            wrap.putInt(animationSigSize);

            wrap.put(animationSignature, 0, animationSigSize);
            wrap.put(animationData);

        }
    }

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
            if (animationHeaderSize <= 0 && wrap.position() >= wrap.limit()) return;
            // Found animation block, getting animation data size
            animationDataSize = wrap.getInt();

            // Get animation signature
            animationSigSize = wrap.getInt();
            animationSignature = new byte[animationSigSize];
            wrap.get(animationSignature, 0, animationSigSize);

            // Animation data
            animationData = new byte[animationDataSize == 0 ? wrap.limit() - wrap.position() : animationDataSize];
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

    public byte[] getBytes() {
        return wrap.array();
    }

    public boolean equals(final PkgWrap other) {
        if (dataSize != other.dataSize) return false;
        if (dataKeyEncryptedSize != other.dataKeyEncryptedSize) return false;
        if (offsetData != other.offsetData) return false;
        if (animationDataSize != other.animationDataSize) return false;
        if (!deepEquals(iv, other.iv)) return false;
        if (!deepEquals(encryptedData, other.encryptedData)) return false;
        if (!deepEquals(signature, other.signature)) return false;
        if (animationSigSize != other.animationSigSize) return false;
        if (sigSize != other.sigSize) return false;
        if (!deepEquals(animationData, other.animationData)) return false;
        if (!deepEquals(animationSignature, other.animationSignature)) return false;

        return true;
    }
}
