package com.aibridge.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExceptionTest {

    @Test
    void configNotFoundException_messageContainsTenantAndFeature() {
        ConfigNotFoundException ex = new ConfigNotFoundException("tenant-1", "feat-a");
        assertTrue(ex.getMessage().contains("tenant-1"));
        assertTrue(ex.getMessage().contains("feat-a"));
    }

    @Test
    void configNotFoundException_withCause() {
        Throwable cause = new IllegalStateException("root");
        ConfigNotFoundException ex = new ConfigNotFoundException("t", "f", cause);
        assertTrue(ex.getMessage().contains("t"));
        assertTrue(ex.getMessage().contains("f"));
        assertSame(cause, ex.getCause());
    }

    @Test
    void queueTimeoutException_messageContainsConfigId() {
        QueueTimeoutException ex = new QueueTimeoutException("cfg-123");
        assertTrue(ex.getMessage().contains("cfg-123"));
    }

    @Test
    void queueTimeoutException_withCause() {
        Throwable cause = new RuntimeException("x");
        QueueTimeoutException ex = new QueueTimeoutException("id", cause);
        assertTrue(ex.getMessage().contains("id"));
        assertSame(cause, ex.getCause());
    }

    @Test
    void providerUnavailableException_constructors() {
        ProviderUnavailableException a = new ProviderUnavailableException("down");
        assertEquals("down", a.getMessage());

        Throwable c = new Exception("c");
        ProviderUnavailableException b = new ProviderUnavailableException("retry", c);
        assertEquals("retry", b.getMessage());
        assertSame(c, b.getCause());
    }

    @Test
    void providerRateLimitException_constructors() {
        ProviderRateLimitException a = new ProviderRateLimitException("429");
        assertEquals("429", a.getMessage());

        Throwable c = new Exception("c");
        ProviderRateLimitException b = new ProviderRateLimitException("limit", c);
        assertEquals("limit", b.getMessage());
        assertSame(c, b.getCause());
    }

    @Test
    void encryptionException_constructors() {
        Throwable cause = new RuntimeException("cipher");
        EncryptionException a = new EncryptionException("bad", cause);
        assertEquals("bad", a.getMessage());
        assertSame(cause, a.getCause());

        EncryptionException b = new EncryptionException(cause);
        assertEquals("Encryption operation failed", b.getMessage());
        assertSame(cause, b.getCause());
    }

    @Test
    void llmValidationException_constructors() {
        LlmValidationException a = new LlmValidationException("invalid");
        assertEquals("invalid", a.getMessage());

        Throwable c = new Exception("c");
        LlmValidationException b = new LlmValidationException("bad", c);
        assertEquals("bad", b.getMessage());
        assertSame(c, b.getCause());
    }

    @Test
    void emptyLlmResponseException_constructors() {
        EmptyLlmResponseException a = new EmptyLlmResponseException("empty");
        assertEquals("empty", a.getMessage());

        Throwable c = new Exception("c");
        EmptyLlmResponseException b = new EmptyLlmResponseException("none", c);
        assertEquals("none", b.getMessage());
        assertSame(c, b.getCause());
    }
}
