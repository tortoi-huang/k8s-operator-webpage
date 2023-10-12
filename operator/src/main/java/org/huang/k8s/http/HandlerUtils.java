package org.huang.k8s.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HandlerUtils {

    public static void sendMessage(HttpExchange httpExchange, int code, String message)
            throws IOException {
        try (var outputStream = httpExchange.getResponseBody()) {
            var bytes = message.getBytes(StandardCharsets.UTF_8);
            httpExchange.sendResponseHeaders(code, bytes.length);
            outputStream.write(bytes);
            outputStream.flush();
        }
    }
}
