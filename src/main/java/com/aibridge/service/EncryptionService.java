package com.aibridge.service;

import com.aibridge.config.EncryptionConfig;
import com.aibridge.exception.EncryptionException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@ApplicationScoped
public class EncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final EncryptionConfig encryptionConfig;
    private final SecureRandom secureRandom = new SecureRandom();

    @Inject
    public EncryptionService(EncryptionConfig encryptionConfig) {
        this.encryptionConfig = encryptionConfig;
    }

    public String encrypt(String plaintext) {
        try {
            byte[] keyBytes =
                    encryptionConfig.getEncryptionKey().getBytes(StandardCharsets.UTF_8);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] cipherWithTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherWithTag.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherWithTag, 0, combined, iv.length, cipherWithTag.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Failed to encrypt payload", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            // A null here has historically meant the value was lost in transit (e.g. stripped by
            // serialization), not that nothing was stored — say so instead of raising an NPE.
            throw new EncryptionException(
                    new IllegalArgumentException("Ciphertext is null or blank — credentials missing"));
        }
        try {
            byte[] keyBytes =
                    encryptionConfig.getEncryptionKey().getBytes(StandardCharsets.UTF_8);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            if (decoded.length < IV_LENGTH) {
                throw new EncryptionException(
                        new IllegalArgumentException("Ciphertext too short for IV"));
            }

            byte[] iv = Arrays.copyOfRange(decoded, 0, IV_LENGTH);
            byte[] cipherBytes = Arrays.copyOfRange(decoded, IV_LENGTH, decoded.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new EncryptionException("Invalid Base64 ciphertext", e);
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Failed to decrypt payload", e);
        }
    }
}
