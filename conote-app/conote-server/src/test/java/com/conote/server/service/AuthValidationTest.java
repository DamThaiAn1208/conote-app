package com.conote.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.conote.common.dto.auth.LoginRequest;
import com.conote.common.dto.auth.RegisterRequest;
import com.conote.common.dto.protocol.ErrorCode;
import com.conote.common.dto.protocol.Operation;
import com.conote.common.dto.protocol.ProtocolRequest;
import com.conote.common.model.User;
import com.conote.server.dao.UserDao;
import com.conote.server.network.RequestDispatcher;
import com.conote.server.security.AuthSessionManager;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthValidationTest {
    @Test
    void rejectsValuesThatCannotBeStoredOrSafelyCheckedByBcrypt() {
        AuthService auth = new AuthService(new UserDao() {
            @Override public boolean existsByEmail(String email) {
                throw new AssertionError("Validation should run before database access");
            }
        });
        assertThrows(IllegalArgumentException.class, () -> auth.register(
                new RegisterRequest("a".repeat(250) + "@x.test", "password", "Name")));
        assertThrows(IllegalArgumentException.class, () -> auth.register(
                new RegisterRequest("a".repeat(81) + "@x.test", "password", "Name")));
        assertThrows(IllegalArgumentException.class, () -> auth.register(
                new RegisterRequest("person@example.test", "password", "N".repeat(121))));
        assertThrows(IllegalArgumentException.class, () -> auth.register(
                new RegisterRequest("person@example.test", "x".repeat(73), "Name")));
        assertThrows(IllegalArgumentException.class, () -> auth.login(
                new LoginRequest("person@example.test", "x".repeat(73))));
    }

    @Test
    void deactivatedAccountLosesProfileAndProtectedOperationAccess() {
        User user = new User();
        user.setUserId(7L);
        user.setEmail("inactive@example.test");
        user.setActive(true);
        UserDao users = new UserDao() {
            @Override public Optional<User> findById(Long userId) {
                return userId.equals(user.getUserId()) ? Optional.of(user) : Optional.empty();
            }
        };
        AuthService auth = new AuthService(users);
        String token = AuthSessionManager.createSession(7L).token();
        user.setActive(false);
        assertThrows(SecurityException.class, () -> auth.currentUser(token));
        assertEquals(ErrorCode.UNAUTHENTICATED, new RequestDispatcher(auth)
                .dispatch(new ProtocolRequest(UUID.randomUUID().toString(),
                        Operation.NOTE_LIST, token, null)).code());
    }
}
