package com.conote.server.controller;

import com.conote.common.dto.ApiResponse;
import com.conote.server.config.EntityManagerUtil;
import com.conote.server.util.JsonResponseWriter;
import com.sun.net.httpserver.HttpExchange;
import jakarta.persistence.EntityManager;

import java.io.IOException;
import java.util.Map;

public class DatabaseHealthController {

    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            JsonResponseWriter.send(
                    exchange,
                    405,
                    ApiResponse.fail("Method Not Allowed")
            );
            return;
        }

        EntityManager entityManager = null;

        try {
            entityManager = EntityManagerUtil.getEntityManager();

            Object result = entityManager
                    .createNativeQuery("SELECT 1")
                    .getSingleResult();

            JsonResponseWriter.send(
                    exchange,
                    200,
                    ApiResponse.success(
                            "Database connection is working",
                            Map.of("result", result)
                    )
            );
        } catch (Exception exception) {
            JsonResponseWriter.send(
                    exchange,
                    500,
                    ApiResponse.fail("Database connection failed: " + exception.getMessage())
            );
        } finally {
            if (entityManager != null && entityManager.isOpen()) {
                entityManager.close();
            }
        }
    }
}