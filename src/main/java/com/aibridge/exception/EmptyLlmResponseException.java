package com.aibridge.exception;

/**
 * Logged when the LLM returns an empty body; not intended to be propagated to API callers.
 */
public class EmptyLlmResponseException extends RuntimeException {

    public EmptyLlmResponseException(String message) {
        super(message);
    }

    public EmptyLlmResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
