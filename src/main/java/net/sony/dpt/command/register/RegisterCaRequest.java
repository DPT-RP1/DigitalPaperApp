package net.sony.dpt.command.register;

import org.bouncycastle.util.encoders.Base64;

import java.util.HashMap;
import java.util.Map;

public class RegisterCaRequest {

    private final byte[] nonce1;
    private final byte[] rHash;
    private final byte[] wrappedRs;
    private final byte[] m4hmac;

    public RegisterCaRequest(byte[] nonce1, byte[] rHash, byte[] wrappedRs, byte[] m4hmac) {
        this.nonce1 = nonce1;
        this.rHash = rHash;
        this.wrappedRs = wrappedRs;
        this.m4hmac = m4hmac;
    }

    public Map<String, String> asMap() {
        return new HashMap<>() {{
            put("a", Base64.toBase64String(nonce1));
            put("b", Base64.toBase64String(rHash));
            put("d", Base64.toBase64String(wrappedRs));
            put("e", Base64.toBase64String(m4hmac));
        }};
    }

}
