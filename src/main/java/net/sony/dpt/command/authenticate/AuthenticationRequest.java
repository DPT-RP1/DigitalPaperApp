package net.sony.dpt.command.authenticate;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class AuthenticationRequest {

    private final String clientId;
    private final byte[] signedNonce;

    public AuthenticationRequest(String clientId, byte[] signedNonce) {
        this.clientId = clientId;
        this.signedNonce = signedNonce;
    }

    public Map<String, Object> toMap() {
        return new HashMap<>() {{
            put("client_id", clientId);
            put("nonce_signed", Base64.getEncoder().encodeToString(signedNonce));
        }};
    }
}
