package net.sony.dpt.command.register;

import org.bouncycastle.util.encoders.Base64;

import java.util.HashMap;
import java.util.Map;

public class RegisterCaRequest {

    private byte[] nonce1;
    private byte[] rHash;
    private byte[] wrappedRs;
    private byte[] m4hmac;

    public RegisterCaRequest(byte[] nonce1, byte[] rHash, byte[] wrappedRs, byte[] m4hmac) {
        this.nonce1 = nonce1;
        this.rHash = rHash;
        this.wrappedRs = wrappedRs;
        this.m4hmac = m4hmac;
    }

    public Map<String, String> asMap() {
        Map<String, String> m4 = new HashMap<>();
        m4.put("a", Base64.toBase64String(nonce1));
        m4.put("b", Base64.toBase64String(rHash));
        m4.put("d", Base64.toBase64String(wrappedRs));
        m4.put("e", Base64.toBase64String(m4hmac));

        return m4;
    }

}
