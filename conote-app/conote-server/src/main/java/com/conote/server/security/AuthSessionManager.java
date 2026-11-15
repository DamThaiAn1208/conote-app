package com.conote.server.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory sessions intentionally expire and are invalidated on server restart. */
public final class AuthSessionManager {
    public static final Duration SESSION_LIFETIME = Duration.ofHours(12);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    private AuthSessionManager() { }

    public static Session createSession(Long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Session session = new Session(token, userId, Instant.now().plus(SESSION_LIFETIME));
        SESSIONS.put(token, session);
        return session;
    }

    public static Optional<Long> findUserIdByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Session session = SESSIONS.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (!Instant.now().isBefore(session.expiresAt())) {
            SESSIONS.remove(token, session);
            return Optional.empty();
        }
        return Optional.of(session.userId());
    }

    public static void removeSession(String token) {
        if (token != null) {
            SESSIONS.remove(token);
        }
    }

    public record Session(String token, Long userId, Instant expiresAt) { }
}
