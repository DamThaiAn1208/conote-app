package com.conote.server.security;

import com.sun.net.httpserver.HttpExchange;

public class AuthHeaderUtil {

    private AuthHeaderUtil() {
    }

    public static String extractBearerToken(HttpExchange exchange) {
        if (exchange == null) {
            return null;
        }

        String authorization = exchange.getRequestHeaders().getFirst("Authorization");

        if (authorization == null || authorization.isBlank()) {
            return null;
        }

        String prefix = "Bearer ";

        if (!authorization.startsWith(prefix)) {
            return null;
        }

        return authorization.substring(prefix.length()).trim();
    }
}