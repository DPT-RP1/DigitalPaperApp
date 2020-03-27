package net.sony.dpt;

import net.sony.util.SimpleHttpClient;
import org.bouncycastle.util.encoders.Base64;

import java.io.IOException;
import java.util.Map;

import static net.sony.util.SimpleHttpClient.fromJSON;

public class HashResponse {

    private byte[] returnedNonce2;
    private byte[] eHash;
    private byte[] m3hmac;

    public HashResponse(byte[] returnedNonce2, byte[] eHash, byte[] m3hmac) {
        this.returnedNonce2 = returnedNonce2;
        this.eHash = eHash;
        this.m3hmac = m3hmac;
    }

    public byte[] getReturnedNonce2() {
        return returnedNonce2;
    }

    public byte[] geteHash() {
        return eHash;
    }

    public byte[] getM3hmac() {
        return m3hmac;
    }

    public static HashResponse fromJson(String json) throws IOException {
        Map<String, String> response = fromJSON(json);
        byte[] returnedNonce2 = Base64.decode(response.get("a"));
        byte[] eHash = Base64.decode(response.get("b"));
        byte[] m3hmac = Base64.decode(response.get("e"));

        return new HashResponse(returnedNonce2, eHash, m3hmac);
    }
}
