package com.aibridge.service;

import com.aibridge.cache.CacheProvider;
import com.aibridge.config.AiBridgeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@ApplicationScoped
public class ApiAuthService {

    private static final String TOKEN_CACHE_PREFIX = "aibridge:api_token:";

    /** Counter key for failed key exchanges, scoped to the caller. */
    private static final String FAILURE_CACHE_PREFIX = "aibridge:auth_fail:";

    /** Failures tolerated inside one window before the caller is locked out. */
    static final int MAX_FAILURES = 10;

    /** How long failures are remembered, and how long a lockout lasts. */
    static final long FAILURE_WINDOW_SECONDS = 300;

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
        return generateToken(apiKey, null);
    }

    /**
     * Validates the API key and issues a bearer token.
     *
     * <p>The static API key is the only credential guarding the admin and completion APIs, and it
     * is guessable at HTTP speed without a limit. Failures are counted per caller for
     * {@value #FAILURE_WINDOW_SECONDS} seconds; past {@value #MAX_FAILURES} the caller is refused
     * outright, without the key even being compared.
     *
     * @param callerId identifies the source for rate limiting; {@code null} disables counting
     * @return the bearer token, or {@code null} if the key is wrong or the caller is locked out
     */
    public String generateToken(String apiKey, String callerId) {
        String failureKey = callerId == null ? null : FAILURE_CACHE_PREFIX + callerId;
        if (failureKey != null && failureCount(failureKey) >= MAX_FAILURES) {
            return null;
        }
        if (!constantTimeEquals(apiKey, config.getAuthApiKey())) {
            if (failureKey != null) {
                recordFailure(failureKey);
            }
            return null;
        }
        if (failureKey != null) {
            cacheProvider.del(failureKey);
        }
        String token = UUID.randomUUID().toString();
        long ttlSeconds = config.getAuthTokenValidityMinutes() * 60L;
        cacheProvider.setex(TOKEN_CACHE_PREFIX + token, ttlSeconds, "valid");
        return token;
    }

    private int failureCount(String failureKey) {
        String raw = cacheProvider.get(failureKey);
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            // A corrupt counter must not hand out an unlimited retry budget.
            return MAX_FAILURES;
        }
    }

    private void recordFailure(String failureKey) {
        // Re-setting the TTL on every failure makes the window rolling: a persistent attacker
        // never ages out of their own lockout.
        cacheProvider.setex(failureKey, FAILURE_WINDOW_SECONDS, String.valueOf(failureCount(failureKey) + 1));
    }

    /**
     * Compares two secrets without leaking their common prefix length through timing.
     * {@code String.equals} returns as soon as it finds a differing character, which lets an
     * attacker recover the key one character at a time.
     */
    static boolean constantTimeEquals(String candidate, String expected) {
        if (candidate == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String cached = cacheProvider.get(TOKEN_CACHE_PREFIX + token);
        return constantTimeEquals(cached, "valid");
    }
}
