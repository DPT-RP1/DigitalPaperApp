package net.sony.dpt.root;


import org.apache.commons.lang3.ArrayUtils;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class FileFetchParser {

    private final byte[] startToken;
    private final byte[] endToken;

    private ByteArrayOutputStream readBuffer;

    private int startPosition;
    private int endPosition;
    private boolean finished;

    public FileFetchParser(final String startToken, final String endToken) {
        this.startToken = startToken.getBytes();
        this.endToken = endToken.getBytes();
        this.startPosition = 0;
        this.endPosition = 0;
        readBuffer = new ByteArrayOutputStream();
        finished = false;
    }

    public boolean finished() {
        return finished;
    }

    public void addBytes(byte[] bytes) {
        if (finished) return;

        for (byte b : bytes) {
            if (b == (byte)'\r' || b == (byte)'\n') continue;

            if (startPosition < startToken.length) {
                if (b == startToken[startPosition]) {
                    startPosition += 1;
                } else {
                    throw new IllegalStateException("The start token " + new String(startToken) + " could not be found.");
                }
            } else {
                if (b == endToken[endPosition]) {
                    endPosition += 1;
                } else {
                    endPosition = 0;
                }

                readBuffer.write(b);

                // We remove the end token only after having written the last byte.
                if (endPosition == endToken.length) {
                    removeEndToken();
                    finished = true;
                    return;
                }
            }

        }
    }

    private void removeEndToken() {
        byte[] original = readBuffer.toByteArray();
        byte[] stripped = ArrayUtils.subarray(original, 0, original.length - endToken.length);
        readBuffer = new ByteArrayOutputStream();
        readBuffer.writeBytes(stripped);

    }

    public int size() {
        return readBuffer.size();
    }

    public byte[] decodeBase64() {
        return Base64.getDecoder().decode(readBuffer.toByteArray());
    }

}
