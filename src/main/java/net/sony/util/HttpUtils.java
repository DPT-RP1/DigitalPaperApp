package net.sony.util;

import java.net.http.HttpResponse;

public class HttpUtils {

    public static <T> boolean ok(HttpResponse<T> response) {
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final String PUT = "PUT";
    public static final String DELETE = "DELETE";

    public static final String CONTENT_TYPE = "Content-Type";

}
