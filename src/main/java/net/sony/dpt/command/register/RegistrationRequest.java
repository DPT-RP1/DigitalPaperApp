package net.sony.dpt.command.register;

import org.bouncycastle.util.encoders.Base64;

import java.util.HashMap;
import java.util.Map;

public class RegistrationRequest {

    private byte[] nonce1;
    private byte[] wrappedDIDKPUBC;
    private byte[] m6hmac;

    public RegistrationRequest(byte[] nonce1, byte[] wrappedDIDKPUBC, byte[] m6hmac) {
        this.nonce1 = nonce1;
        this.wrappedDIDKPUBC = wrappedDIDKPUBC;
        this.m6hmac = m6hmac;
    }

    public Map<String, String> asMap() {
        Map<String, String> m6 = new HashMap<>();
        m6.put("a", Base64.toBase64String(nonce1));
        m6.put("d", Base64.toBase64String(wrappedDIDKPUBC));
        m6.put("e", Base64.toBase64String(m6hmac));
        return m6;
    }

}
