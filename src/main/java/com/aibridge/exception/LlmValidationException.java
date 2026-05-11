package com.aibridge.exception;

/**
 * Raised when LLM output or related data fails application validation rules.
 */
public class LlmValidationException extends RuntimeException {

    public LlmValidationException(String message) {
        super(message);
    }

    public LlmValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
