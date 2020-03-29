package net.sony.dpt.command.authenticate;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;

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

    public void insertInCookieManager(URI uri, CookieManager manager) {
        HttpCookie httpCookie = new HttpCookie("Credentials", credentials);
        httpCookie.setSecure(true);
        httpCookie.setHttpOnly(false);
        httpCookie.setDomain(uri.getHost());
        httpCookie.setPortlist(uri.getPort() + "");
        httpCookie.setPath("/");
        manager.getCookieStore().add(uri, httpCookie);
    }

    /**
     * This allow to pass a lambda to write the cookie
     */
    @FunctionalInterface
    public interface Request {
        void setHeader(String header, String value);
    }

    @Override
    public String toString() {
        return "AuthenticationCookie{" +
                "credentials='" + credentials + '\'' +
                '}';
    }
}
