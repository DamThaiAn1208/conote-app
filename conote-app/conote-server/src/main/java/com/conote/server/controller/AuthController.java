package com.conote.server.controller;

import com.conote.common.dto.ApiResponse;
import com.conote.common.dto.auth.RegisterRequest;
import com.conote.common.model.User;
import com.conote.server.service.AuthService;
import com.conote.server.util.JsonRequestReader;
import com.conote.server.util.JsonResponseWriter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;

public class AuthController {
    private final AuthService authService = new AuthService();

    public void handleRegister(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            JsonResponseWriter.send(
                    exchange,
                    405,
                    ApiResponse.fail("Method Not Allowed")
            );
            return;
        }

        try {
            RegisterRequest request = JsonRequestReader.read(exchange, RegisterRequest.class);
            User createdUser = authService.register(request);

            Map<String, Object> data = Map.of(
                    "userName", createdUser.getUserName(),
                    "email", createdUser.getEmail(),
                    "fullName", createdUser.getFullName(),
                    "verified", createdUser.getVerified(),
                    "active", createdUser.getActive(),
                    "passwordHashPreview", createdUser.getPasswordHash().substring(0, 10) + "..."
            );

            JsonResponseWriter.send(
                    exchange,
                    201,
                    ApiResponse.success("Register successfully", data)
            );
        } catch (IllegalArgumentException exception) {
            JsonResponseWriter.send(
                    exchange,
                    400,
                    ApiResponse.fail(exception.getMessage())
            );
        } catch (Exception exception) {
            JsonResponseWriter.send(
                    exchange,
                    500,
                    ApiResponse.fail("Internal server error")
            );
        }
    }
}