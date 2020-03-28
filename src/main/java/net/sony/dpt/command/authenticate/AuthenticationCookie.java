package net.sony.dpt.command.authenticate;

public class AuthenticationCookie {

    /**
     * Credentials: base64 token
     */
    private String credentials;

    public AuthenticationCookie(String credentials) {
        this.credentials = credentials;
    }

    public String getCredentials() {
        return credentials;
    }

    public void insertInRequest(Request req) {
        req.setHeader("Cookie", "Credentials=" + credentials);
    }

    /**
     * This allow to pass a lambda to write the cookie
     */
    @FunctionalInterface
    public interface Request {
        void setHeader(String header, String value);
    }

}
