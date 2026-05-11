package com.aibridge.exception;

/**
 * Wraps low-level cryptography failures (e.g. cipher, keystore) for uniform handling.
 */
public class EncryptionException extends RuntimeException {

    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }

    public EncryptionException(Throwable cause) {
        super("Encryption operation failed", cause);
    }
}
