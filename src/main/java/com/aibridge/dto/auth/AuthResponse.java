package com.aibridge.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AuthResponse {

    private String token;

    @JsonProperty("expires_in_minutes")
    private int expiresInMinutes;

    public AuthResponse() {}

    public AuthResponse(String token, int expiresInMinutes) {
        this.token = token;
        this.expiresInMinutes = expiresInMinutes;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getExpiresInMinutes() {
        return expiresInMinutes;
    }

    public void setExpiresInMinutes(int expiresInMinutes) {
        this.expiresInMinutes = expiresInMinutes;
    }
}
