package net.sony.dpt.command.authenticate;

import net.sony.dpt.DigitalPaperEndpoint;
import net.sony.dpt.command.register.RegistrationResponse;
import net.sony.util.CryptographyUtil;
import net.sony.util.SimpleHttpClient;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * def authenticate(self, client_id, key):
 * <p>
 * url = "{base_url}/auth".format(base_url = self.base_url)
 * data = {
 * "client_id": client_id,
 * "nonce_signed": signed_nonce
 * }
 * r = self.session.put(url, json=data)
 * # cookiejar cannot parse the cookie format used by the tablet,
 * # so we have to set it manually.
 * _, credentials = r.headers["Set-Cookie"].split("; ")[0].split("=")
 * self.session.cookies["Credentials"] = credentials
 * return r
 */
public class AuthenticateCommand {

    private static final int PORT = 8443;
    private final CryptographyUtil cryptographyUtil;
    private final DigitalPaperEndpoint digitalPaperEndpoint;
    private final SimpleHttpClient simpleHttpClient;
    private final String baseUrl;

    public AuthenticateCommand(
            String addr,
            DigitalPaperEndpoint digitalPaperEndpoint,
            CryptographyUtil cryptographyUtil,
            SimpleHttpClient simpleHttpClient) {
        this.cryptographyUtil = cryptographyUtil;
        this.digitalPaperEndpoint = digitalPaperEndpoint;
        this.baseUrl = "https://" + addr + ":" + PORT;
        this.simpleHttpClient = simpleHttpClient;
    }

    public AuthenticationCookie authenticate(RegistrationResponse registrationResponse) throws Exception {
        byte[] signedNonce = cryptographyUtil.signSHA256RSA(
                digitalPaperEndpoint.getNonce(registrationResponse.getClientId()).getBytes(StandardCharsets.UTF_8),
                registrationResponse.getPrivateKey()
        );

        AuthenticationRequest authenticationRequest = new AuthenticationRequest(registrationResponse.getClientId(), signedNonce);
        HttpResponse<String> response = simpleHttpClient.putWithResponse(baseUrl + "/auth", authenticationRequest.toMap());

        String credentials = response.headers().map().get("set-cookie").get(0).split("; ")[0].split("=")[1];
        return new AuthenticationCookie(credentials);
    }

}
