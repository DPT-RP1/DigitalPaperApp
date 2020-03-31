package net.sony.dpt.command.register;

import org.bouncycastle.util.encoders.Base64;

import java.util.HashMap;
import java.util.Map;

public class RegistrationRequest {

    private final byte[] nonce1;
    private final byte[] wrappedDIDKPUBC;
    private final byte[] m6hmac;

    public RegistrationRequest(byte[] nonce1, byte[] wrappedDIDKPUBC, byte[] m6hmac) {
        this.nonce1 = nonce1;
        this.wrappedDIDKPUBC = wrappedDIDKPUBC;
        this.m6hmac = m6hmac;
    }

    public Map<String, Object> asMap() {
        return new HashMap<>() {{
            put("a", Base64.toBase64String(nonce1));
            put("d", Base64.toBase64String(wrappedDIDKPUBC));
            put("e", Base64.toBase64String(m6hmac));
        }};
    }

}
