package com.conote.server.security;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthSessionManager {
    private static final Map<String, Long> TOKEN_TO_USER_ID = new ConcurrentHashMap<>();

    private AuthSessionManager() {
    }

    public static String createSession(Long userId) {
        String token = UUID.randomUUID().toString();
        TOKEN_TO_USER_ID.put(token, userId);
        return token;
    }

    public static Optional<Long> findUserIdByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(TOKEN_TO_USER_ID.get(token));
    }

    public static void removeSession(String token) {
        if (token != null && !token.isBlank()) {
            TOKEN_TO_USER_ID.remove(token);
        }
    }
}