package net.sony.util;

import java.net.http.HttpResponse;

public class HttpUtils {

    public static <T> boolean ok(HttpResponse<T> response) {
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }
}
