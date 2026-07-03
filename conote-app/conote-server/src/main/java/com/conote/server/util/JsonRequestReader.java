package com.conote.server.util;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class JsonRequestReader {
    private static final Gson gson = new Gson();

    private JsonRequestReader() {
    }

    public static <T> T read(HttpExchange exchange, Class<T> clazz) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(
                exchange.getRequestBody(),
                StandardCharsets.UTF_8
        )) {
            return gson.fromJson(reader, clazz);
        }
    }
}