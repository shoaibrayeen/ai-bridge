package com.aibridge.exception;

/**
 * Raised when the provider responds with HTTP 429 (rate limited).
 */
public class ProviderRateLimitException extends RuntimeException {

    public ProviderRateLimitException(String message) {
        super(message);
    }

    public ProviderRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
