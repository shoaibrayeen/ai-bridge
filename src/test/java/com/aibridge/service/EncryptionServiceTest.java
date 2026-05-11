package com.aibridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.aibridge.config.EncryptionConfig;
import com.aibridge.exception.EncryptionException;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EncryptionServiceTest {

    private static final String AES_256_KEY_32 = "01234567890123456789012345678901";

    @Mock
    EncryptionConfig encryptionConfig;

    @InjectMocks
    EncryptionService encryptionService;

    @BeforeEach
    void stubKey() {
        when(encryptionConfig.getEncryptionKey()).thenReturn(AES_256_KEY_32);
    }

    @Test
    void encryptThenDecrypt_returnsOriginalPlaintext() {
        String original = "secret-payload-123";
        String cipher = encryptionService.encrypt(original);
        assertEquals(original, encryptionService.decrypt(cipher));
    }

    @Test
    void decryptWithInvalidData_throwsEncryptionException() {
        assertThrows(EncryptionException.class, () -> encryptionService.decrypt("not-valid-base64!!!"));
        assertThrows(EncryptionException.class, () -> encryptionService.decrypt("abcd"));
    }

    @Test
    void decryptWithCiphertextTooShort_throwsEncryptionException() {
        // Base64 decodes to fewer than 12 bytes (IV length)
        String tooShort = Base64.getEncoder().encodeToString(new byte[] {1});
        EncryptionException ex =
                assertThrows(EncryptionException.class, () -> encryptionService.decrypt(tooShort));
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void decryptWithInvalidBase64_throwsEncryptionExceptionWithIllegalArgumentCause() {
        EncryptionException ex =
                assertThrows(
                        EncryptionException.class,
                        () -> encryptionService.decrypt("@@@not-base64@@@"));
        assertTrue(ex.getMessage().contains("Invalid Base64"));
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void decryptWithTamperedCiphertext_throwsEncryptionExceptionFromGeneralSecurity() {
        String cipher = encryptionService.encrypt("payload");
        byte[] raw = Base64.getDecoder().decode(cipher);
        raw[raw.length - 1] ^= (byte) 0x5a;
        String tampered = Base64.getEncoder().encodeToString(raw);
        EncryptionException ex =
                assertThrows(EncryptionException.class, () -> encryptionService.decrypt(tampered));
        assertTrue(ex.getMessage().contains("Failed to decrypt"));
        assertTrue(ex.getCause() instanceof java.security.GeneralSecurityException);
    }

    @Test
    void encryptWithInvalidKeyLength_throwsEncryptionExceptionWrappingGeneralSecurity() {
        when(encryptionConfig.getEncryptionKey()).thenReturn("012345678901234");
        EncryptionException ex =
                assertThrows(EncryptionException.class, () -> encryptionService.encrypt("x"));
        assertTrue(ex.getMessage().contains("Failed to encrypt"));
        assertTrue(ex.getCause() instanceof java.security.GeneralSecurityException);
    }

    @Test
    void encryptDecrypt_emptyString_roundTrips() {
        String cipher = encryptionService.encrypt("");
        assertEquals("", encryptionService.decrypt(cipher));
    }

    @Test
    void encryptDecrypt_shortAndLongStrings_roundTrip() {
        String shortStr = "hi";
        assertEquals(shortStr, encryptionService.decrypt(encryptionService.encrypt(shortStr)));

        String longStr = "x".repeat(50_000);
        assertEquals(longStr, encryptionService.decrypt(encryptionService.encrypt(longStr)));
    }
}
