package com.conote.common.dto.auth;

public class LoginResponse {
    private String token;
    private Long userId;
    private String userName;
    private String email;
    private String fullName;
    private Boolean verified;
    private Boolean active;

    public LoginResponse() {
    }

    public LoginResponse(
            String token,
            Long userId,
            String userName,
            String email,
            String fullName,
            Boolean verified,
            Boolean active
    ) {
        this.token = token;
        this.userId = userId;
        this.userName = userName;
        this.email = email;
        this.fullName = fullName;
        this.verified = verified;
        this.active = active;
    }

    public String getToken() {
        return token;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public Boolean getVerified() {
        return verified;
    }

    public Boolean getActive() {
        return active;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}