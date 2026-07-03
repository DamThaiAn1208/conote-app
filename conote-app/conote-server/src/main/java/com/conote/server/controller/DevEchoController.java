package com.conote.server.controller;

import com.conote.common.dto.ApiResponse;
import com.conote.server.util.JsonRequestReader;
import com.conote.server.util.JsonResponseWriter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;

public class DevEchoController {

    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            JsonResponseWriter.send(
                    exchange,
                    405,
                    ApiResponse.fail("Method Not Allowed")
            );
            return;
        }

        Map requestBody = JsonRequestReader.read(exchange, Map.class);

        JsonResponseWriter.send(
                exchange,
                200,
                ApiResponse.success("Server received your JSON", requestBody)
        );
    }
}