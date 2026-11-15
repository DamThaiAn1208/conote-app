package com.conote.common.dto.auth;

/** Network-only auth payloads; never serialize User JPA entities. */
public final class AuthDtos {
    private AuthDtos() { }

    public record UserView(long userId, String userName, String email, String fullName) { }

    public record SessionView(String token, String expiresAt, UserView user) { }
}
