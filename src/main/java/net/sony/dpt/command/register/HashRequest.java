package net.sony.dpt.command.register;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class HashRequest {

    private final byte[] authKey;
    private final byte[] keyWrapKey;
    private final byte[] m2hmac;

    private final byte[] nonce1;
    private final byte[] nonce2;
    private final byte[] publicKey;
    private final byte[] mac;

    public HashRequest(byte[] authKey, byte[] keyWrapKey, byte[] m2hmac, byte[] nonce1, byte[] nonce2, byte[] publicKey, byte[] mac) {
        this.authKey = authKey;
        this.keyWrapKey = keyWrapKey;
        this.m2hmac = m2hmac;
        this.nonce1 = nonce1;
        this.nonce2 = nonce2;
        this.publicKey = publicKey;
        this.mac = mac;
    }

    public byte[] getAuthKey() {
        return authKey;
    }

    public byte[] getKeyWrapKey() {
        return keyWrapKey;
    }

    public byte[] getM2hmac() {
        return m2hmac;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public byte[] getNonce2() {
        return nonce2;
    }

    public Map<String, String> asMap() {
        Map<String, String> m2 = new HashMap<>();
        m2.put("a", Base64.getEncoder().encodeToString(nonce1));
        m2.put("b", Base64.getEncoder().encodeToString(nonce2));
        m2.put("c", Base64.getEncoder().encodeToString(mac));
        m2.put("d", Base64.getEncoder().encodeToString(publicKey));
        m2.put("e", Base64.getEncoder().encodeToString(m2hmac));
        return m2;
    }
}
