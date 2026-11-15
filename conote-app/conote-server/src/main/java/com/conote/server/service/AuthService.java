package com.conote.server.service;

import com.conote.common.dto.auth.AuthDtos;
import com.conote.common.dto.auth.LoginRequest;
import com.conote.common.dto.auth.RegisterRequest;
import com.conote.common.model.User;
import com.conote.server.dao.UserDao;
import com.conote.server.security.AuthSessionManager;
import org.mindrot.jbcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

public class AuthService {
    private final UserDao userDao;

    public AuthService() {
        this(new UserDao());
    }

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    public AuthDtos.SessionView login(LoginRequest request) {
        validateLoginRequest(request);
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        User user = userDao.findByEmail(normalizedEmail)
                .orElseThrow(() -> new SecurityException("Invalid email or password"));
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new SecurityException("Account is inactive");
        }
        if (!BCrypt.checkpw(request.getPassword(), user.getPasswordHash())) {
            throw new SecurityException("Invalid email or password");
        }
        AuthSessionManager.Session session = AuthSessionManager.createSession(user.getUserId());
        return new AuthDtos.SessionView(
                session.token(), session.expiresAt().toString(), toView(user));
    }

    public User register(RegisterRequest request) {
        validateRegisterRequest(request);
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userDao.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException();
        }
        User user = new User();
        user.setUserName(resolveUserName(normalizedEmail));
        user.setEmail(normalizedEmail);
        user.setPasswordHash(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));
        user.setFullName(request.getFullName().trim());
        user.setAvatar(null);
        user.setVerified(false);
        user.setActive(true);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userDao.save(user);
    }

    public AuthDtos.UserView currentUser(String token) {
        Long userId = AuthSessionManager.findUserIdByToken(token)
                .orElseThrow(() -> new SecurityException("Invalid or expired token"));
        User user = userDao.findById(userId)
                .orElseThrow(() -> new SecurityException("Invalid or expired token"));
        if (!Boolean.TRUE.equals(user.getActive())) {
            AuthSessionManager.removeSession(token);
            throw new SecurityException("Account is inactive");
        }
        return toView(user);
    }

    public void logout(String token) {
        if (AuthSessionManager.findUserIdByToken(token).isEmpty()) {
            throw new SecurityException("Invalid or expired token");
        }
        AuthSessionManager.removeSession(token);
    }

    public static AuthDtos.UserView toView(User user) {
        return new AuthDtos.UserView(
                user.getUserId(), user.getUserName(), user.getEmail(), user.getFullName());
    }

    private void validateLoginRequest(LoginRequest request) {
        if (request == null || isBlank(request.getEmail()) || isBlank(request.getPassword())) {
            throw new IllegalArgumentException("Email and password are required");
        }
        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes");
        }
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null || isBlank(request.getEmail()) || isBlank(request.getPassword())
                || isBlank(request.getFullName())) {
            throw new IllegalArgumentException("Email, password and full name are required");
        }
        String email = request.getEmail().trim();
        if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+")
                || email.substring(0, email.indexOf('@')).length() > 80) {
            throw new IllegalArgumentException("Email is invalid");
        }
        if (request.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes");
        }
        String fullName = request.getFullName().trim();
        if (fullName.codePointCount(0, fullName.length()) > 120) {
            throw new IllegalArgumentException("Full name must not exceed 120 characters");
        }
    }

    private String resolveUserName(String email) {
        return email.substring(0, email.indexOf('@'));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static final class DuplicateEmailException extends RuntimeException {
        public DuplicateEmailException() {
            super("Email already exists");
        }
    }
}
