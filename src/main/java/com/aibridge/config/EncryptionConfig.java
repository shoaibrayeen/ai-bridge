package com.aibridge.config;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class EncryptionConfig {

    private static final int AES_256_KEY_LENGTH_CHARS = 32;

    @ConfigProperty(name = "aibridge.encryption.key")
    String encryptionKey;

    @PostConstruct
    void validateKey() {
        if (encryptionKey == null || encryptionKey.length() != AES_256_KEY_LENGTH_CHARS) {
            throw new IllegalStateException(
                    "aibridge.encryption.key must be exactly 32 characters for AES-256");
        }
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }
}
