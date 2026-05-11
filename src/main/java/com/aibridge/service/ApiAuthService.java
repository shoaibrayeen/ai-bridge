package com.aibridge.service;

import com.aibridge.cache.CacheProvider;
import com.aibridge.config.AiBridgeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;

@ApplicationScoped
public class ApiAuthService {

    private static final String TOKEN_CACHE_PREFIX = "aibridge:api_token:";

    @Inject
    CacheProvider cacheProvider;

    @Inject
    AiBridgeConfig config;

    /**
     * Validates the supplied API key and, if correct, generates an opaque bearer
     * token cached for the configured validity period.
     *
     * @return the bearer token, or {@code null} if the API key is invalid
     */
    public String generateToken(String apiKey) {
        if (apiKey == null || !apiKey.equals(config.getAuthApiKey())) {
            return null;
        }
        String token = UUID.randomUUID().toString();
        long ttlSeconds = config.getAuthTokenValidityMinutes() * 60L;
        cacheProvider.setex(TOKEN_CACHE_PREFIX + token, ttlSeconds, "valid");
        return token;
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String cached = cacheProvider.get(TOKEN_CACHE_PREFIX + token);
        return "valid".equals(cached);
    }
}
