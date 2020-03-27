package net.sony.dpt;

import org.bouncycastle.util.encoders.Base64;

import java.io.IOException;
import java.util.Map;

import static net.sony.util.SimpleHttpClient.fromJSON;

/**
 *         Map<String, String> pinResponse = fromJSON(simpleHttpClient.post(registerPinUrl));
 *
 *         byte[] nonce1 = b64decoder.decode(pinResponse.get("a"));
 *         byte[] mac = b64decoder.decode(pinResponse.get("b"));
 *         byte[] otherContribution = b64decoder.decode(pinResponse.get("c"));
 */
public class PinResponse {

    private byte[] nonce1;
    private byte[] mac;
    private byte[] otherContribution;

    public PinResponse(byte[] nonce1, byte[] mac, byte[] otherContribution) {
        this.nonce1 = nonce1;
        this.mac = mac;
        this.otherContribution = otherContribution;
    }

    public static PinResponse fromJson(String json) throws IOException {
        Map<String, String> pinResponse = fromJSON(json);

        byte[] nonce1 = Base64.decode(pinResponse.get("a"));
        byte[] mac = Base64.decode(pinResponse.get("b"));
        byte[] otherContribution = Base64.decode(pinResponse.get("c"));

        return new PinResponse(nonce1, mac, otherContribution);
    }

    public byte[] getNonce1() {
        return nonce1;
    }

    public byte[] getMac() {
        return mac;
    }

    public byte[] getOtherContribution() {
        return otherContribution;
    }
}
