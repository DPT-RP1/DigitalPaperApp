package net.sony.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static java.nio.charset.StandardCharsets.*;

public class HashUtils {

    public static String sha256Hex(final String clear) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return ByteUtils.bytesToHex(digest.digest(clear.getBytes(UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Are you running this on a computer or a coffee machine ?", e);
        }

    }

}
