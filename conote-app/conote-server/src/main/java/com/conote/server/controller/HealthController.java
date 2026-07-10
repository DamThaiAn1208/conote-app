package com.conote.server.controller;

import com.conote.common.dto.ApiResponse;
import com.conote.server.util.JsonResponseWriter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;

public class HealthController {

    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            JsonResponseWriter.send(
                    exchange,
                    405,
                    ApiResponse.fail("Method Not Allowed")
            );
            return;
        }

        Map<String, String> data = Map.of(
                "status", "ok",
                "service", "conote-server"
        );

        JsonResponseWriter.send(
                exchange,
                200,
                ApiResponse.success("CoNote server is running", data)
        );
    }
}