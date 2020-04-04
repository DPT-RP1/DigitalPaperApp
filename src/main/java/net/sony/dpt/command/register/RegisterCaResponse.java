package net.sony.dpt.command.register;

import org.bouncycastle.util.encoders.Base64;

import java.io.IOException;
import java.util.Map;

import static net.sony.util.SimpleHttpClient.fromJSON;

public class RegisterCaResponse {

    private final byte[] returnedNonce2;
    private final byte[] wrappedEsCert;
    private final byte[] m5hmac;
    private byte[] cert;

    public RegisterCaResponse(byte[] returnedNonce2, byte[] wrappedEsCert, byte[] m5hmac) {
        this.returnedNonce2 = returnedNonce2;
        this.wrappedEsCert = wrappedEsCert;
        this.m5hmac = m5hmac;
    }

    public static RegisterCaResponse fromJson(String json) throws IOException {
        Map<String, Object> m5 = fromJSON(json);
        byte[] returnedNonce2 = org.bouncycastle.util.encoders.Base64.decode((String) m5.get("a"));
        byte[] wrappedEsCert = org.bouncycastle.util.encoders.Base64.decode((String) m5.get("d"));
        byte[] m5hmac = Base64.decode((String) m5.get("e"));

        return new RegisterCaResponse(returnedNonce2, wrappedEsCert, m5hmac);
    }

    public byte[] getReturnedNonce2() {
        return returnedNonce2;
    }

    public byte[] getWrappedEsCert() {
        return wrappedEsCert;
    }

    public byte[] getM5hmac() {
        return m5hmac;
    }

    public byte[] getCert() {
        return cert;
    }

    public void setCert(byte[] cert) {
        this.cert = cert;
    }
}
