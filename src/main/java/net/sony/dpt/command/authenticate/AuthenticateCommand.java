package net.sony.dpt.command.authenticate;

import net.sony.dpt.DigitalPaperEndpoint;
import net.sony.dpt.command.register.RegistrationResponse;
import net.sony.util.CryptographyUtil;

import java.nio.charset.StandardCharsets;

public class AuthenticateCommand {

    private final CryptographyUtil cryptographyUtil;
    private final DigitalPaperEndpoint digitalPaperEndpoint;

    public AuthenticateCommand(
            DigitalPaperEndpoint digitalPaperEndpoint,
            CryptographyUtil cryptographyUtil) {
        this.cryptographyUtil = cryptographyUtil;
        this.digitalPaperEndpoint = digitalPaperEndpoint;
    }

    public AuthenticationCookie authenticate(RegistrationResponse registrationResponse) throws Exception {
        byte[] signedNonce = cryptographyUtil.signSHA256RSA(
                digitalPaperEndpoint.getNonce(registrationResponse.getClientId()).getBytes(StandardCharsets.UTF_8),
                registrationResponse.getPrivateKey()
        );

        AuthenticationRequest authenticationRequest = new AuthenticationRequest(registrationResponse.getClientId(), signedNonce);
        String credentials = digitalPaperEndpoint.authenticate(authenticationRequest.toMap());
        return new AuthenticationCookie(credentials);
    }

}
