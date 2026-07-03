package com.conote.server.controller;

import com.conote.common.dto.ApiResponse;
import com.conote.common.dto.auth.LoginRequest;
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
                    "userId", createdUser.getUserId(),
                    "userName", createdUser.getUserName(),
                    "email", createdUser.getEmail(),
                    "fullName", createdUser.getFullName(),
                    "verified", createdUser.getVerified(),
                    "active", createdUser.getActive()
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

    public void handleLogin(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            JsonResponseWriter.send(
                    exchange,
                    405,
                    ApiResponse.fail("Method Not Allowed")
            );
            return;
        }

        try {
            LoginRequest request = JsonRequestReader.read(exchange, LoginRequest.class);
            User user = authService.login(request);

            Map<String, Object> data = Map.of(
                    "userId", user.getUserId(),
                    "userName", user.getUserName(),
                    "email", user.getEmail(),
                    "fullName", user.getFullName(),
                    "verified", user.getVerified(),
                    "active", user.getActive()
            );

            JsonResponseWriter.send(
                    exchange,
                    200,
                    ApiResponse.success("Login successfully", data)
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