package com.conote.server.util;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class JsonResponseWriter {
    private static final Gson gson = new Gson();

    private JsonResponseWriter() {
    }

    public static void send(HttpExchange exchange, int statusCode, Object responseBody)
            throws IOException {
        String json = gson.toJson(responseBody);
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}