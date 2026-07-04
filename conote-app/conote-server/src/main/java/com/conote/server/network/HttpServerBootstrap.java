package com.conote.server.network;

import com.conote.server.controller.AuthController;
import com.conote.server.controller.DatabaseHealthController;
import com.conote.server.controller.DevEchoController;
import com.conote.server.controller.HealthController;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class HttpServerBootstrap {
    private static final int DEFAULT_PORT = 8080;

    private final int port;
    private HttpServer server;

    public HttpServerBootstrap() {
        this(DEFAULT_PORT);
    }

    public HttpServerBootstrap(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        HealthController healthController = new HealthController();
        DevEchoController devEchoController = new DevEchoController();
        AuthController authController = new AuthController();
        DatabaseHealthController databaseHealthController = new DatabaseHealthController();


        server.createContext("/", this::handleRoot);
        server.createContext("/api/health", healthController::handle);
        server.createContext("/api/dev/echo", devEchoController::handle);
        server.createContext("/api/auth/register", authController::handleRegister);
        server.createContext("/api/auth/login", authController::handleLogin);
        server.createContext("/api/health/database", databaseHealthController::handle);
        server.createContext("/api/auth/me", authController::handleMe);

        server.setExecutor(null);
        server.start();

        System.out.println("CoNote server started at http://localhost:" + port);
        System.out.println("Health check: http://localhost:" + port + "/api/health");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleRoot(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }

        sendJson(exchange, 200, "{\"message\":\"CoNote server is running\",\"health\":\"/api/health\"}");
    }

    private void sendJson(com.sun.net.httpserver.HttpExchange exchange, int statusCode, String json)
            throws IOException {
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}