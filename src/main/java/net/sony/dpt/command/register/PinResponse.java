package net.sony.dpt.command.register;

import net.sony.util.BigIntegerUtils;
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
    private byte[] rawOtherContribution;

    public PinResponse(byte[] nonce1, byte[] mac, byte[] rawOtherContribution, byte[] otherContribution) {
        this.nonce1 = nonce1;
        this.mac = mac;
        this.otherContribution = otherContribution;
        this.rawOtherContribution = rawOtherContribution;
    }

    public static PinResponse fromJson(String json) throws IOException {
        Map<String, String> pinResponse = fromJSON(json);

        byte[] nonce1 = Base64.decode(pinResponse.get("a"));
        byte[] mac = Base64.decode(pinResponse.get("b"));
        byte[] rawOtherContribution = Base64.decode(pinResponse.get("c"));

        byte[] otherContribution = BigIntegerUtils.projectArray(rawOtherContribution, 256);
        return new PinResponse(nonce1, mac, rawOtherContribution, otherContribution);
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

    public byte[] getRawOtherContribution() {
        return rawOtherContribution;
    }
}
