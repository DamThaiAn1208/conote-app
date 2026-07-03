package com.conote.server.service;

import com.conote.common.dto.auth.LoginRequest;
import com.conote.common.dto.auth.RegisterRequest;
import com.conote.common.model.User;
import com.conote.server.dao.UserDao;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;

public class AuthService {
    private final UserDao userDao = new UserDao();

    public User login(LoginRequest request) {
        validateLoginRequest(request);

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        User user = userDao.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new IllegalArgumentException("Account is inactive");
        }

        boolean passwordMatches = BCrypt.checkpw(
                request.getPassword(),
                user.getPasswordHash()
        );

        if (!passwordMatches) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        return user;
    }

    private void validateLoginRequest(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        if (isBlank(request.getEmail())) {
            throw new IllegalArgumentException("Email is required");
        }

        if (isBlank(request.getPassword())) {
            throw new IllegalArgumentException("Password is required");
        }
    }

    public User register(RegisterRequest request) {
        validateRegisterRequest(request);

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userDao.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email already exists");
        }

        String passwordHash = BCrypt.hashpw(request.getPassword(), BCrypt.gensalt());

        User user = new User();
        user.setUserName(resolveUserName(normalizedEmail));
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordHash);
        user.setFullName(normalizeFullName(request.getFullName()));
        user.setAvatar(null);
        user.setVerified(false);
        user.setActive(true);

        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        return userDao.save(user);
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        if (isBlank(request.getEmail())) {
            throw new IllegalArgumentException("Email is required");
        }

        if (!request.getEmail().contains("@")) {
            throw new IllegalArgumentException("Email is invalid");
        }

        if (isBlank(request.getPassword())) {
            throw new IllegalArgumentException("Password is required");
        }

        if (request.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }

        if (isBlank(request.getFullName())) {
            throw new IllegalArgumentException("Full name is required");
        }
    }

    private String resolveUserName(String email) {
        int atIndex = email.indexOf("@");
        if (atIndex > 0) {
            return email.substring(0, atIndex);
        }
        return email;
    }

    private String normalizeFullName(String fullName) {
        return fullName == null ? "" : fullName.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}