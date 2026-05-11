package com.aibridge.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class EncryptionConfigTest {

    private static final String VALID_KEY = "01234567890123456789012345678901";

    @Test
    void validateKey_with32CharKey_succeedsAndGetterReturnsKey() throws Exception {
        EncryptionConfig config = new EncryptionConfig();
        setEncryptionKey(config, VALID_KEY);

        invokeValidateKeyUnchecked(config);

        assertEquals(VALID_KEY, config.getEncryptionKey());
    }

    @Test
    void validateKey_withNullKey_throwsIllegalStateException() throws Exception {
        EncryptionConfig config = new EncryptionConfig();
        setEncryptionKey(config, null);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> invokeValidateKeyUnchecked(config));
        assertEquals(
                "aibridge.encryption.key must be exactly 32 characters for AES-256",
                ex.getMessage());
    }

    @Test
    void validateKey_withShortKey_throwsIllegalStateException() throws Exception {
        EncryptionConfig config = new EncryptionConfig();
        setEncryptionKey(config, "short");

        assertThrows(IllegalStateException.class, () -> invokeValidateKeyUnchecked(config));
    }

    @Test
    void validateKey_withLongKey_throwsIllegalStateException() throws Exception {
        EncryptionConfig config = new EncryptionConfig();
        setEncryptionKey(config, VALID_KEY + "x");

        assertThrows(IllegalStateException.class, () -> invokeValidateKeyUnchecked(config));
    }

    private static void setEncryptionKey(EncryptionConfig config, String value) throws Exception {
        Field f = EncryptionConfig.class.getDeclaredField("encryptionKey");
        f.setAccessible(true);
        f.set(config, value);
    }

    private static void invokeValidateKeyUnchecked(EncryptionConfig config) {
        try {
            Method m = EncryptionConfig.class.getDeclaredMethod("validateKey");
            m.setAccessible(true);
            m.invoke(config);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) {
                throw re;
            }
            if (c instanceof Error err) {
                throw err;
            }
            throw new RuntimeException(c);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
