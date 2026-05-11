package com.aibridge.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiBridgeConfigTest {

    @Test
    void gettersReturnValuesSetViaReflection() throws Exception {
        AiBridgeConfig config = new AiBridgeConfig();

        setField(config, "uiRequired", false);
        setField(config, "encryptionKey", "test-encryption-key");
        setField(config, "queueTimeoutMs", 12345);
        setField(config, "queuePollIntervalMs", 99);
        setField(config, "queueMaxDepth", 42);
        setField(config, "cacheMode", "redis");
        setField(config, "authApiKey", "test-key");
        setField(config, "authTokenValidityMinutes", 120);
        setField(config, "logLevel", "WARN");
        setField(config, "logAppLevel", "TRACE");
        setField(config, "loadTestMaxParallel", 777);
        setField(config, "allowedProviderHosts", Optional.of(List.of("api.openai.com", "bedrock.aws")));

        assertFalse(config.isUiRequired());
        assertEquals("test-encryption-key", config.getEncryptionKey());
        assertEquals(12345, config.getQueueTimeoutMs());
        assertEquals(99, config.getQueuePollIntervalMs());
        assertEquals(42, config.getQueueMaxDepth());
        assertEquals("redis", config.getCacheMode());
        assertEquals("test-key", config.getAuthApiKey());
        assertEquals(120, config.getAuthTokenValidityMinutes());
        assertEquals("WARN", config.getLogLevel());
        assertEquals("TRACE", config.getLogAppLevel());
        assertEquals(777, config.getLoadTestMaxParallel());
        assertTrue(config.getAllowedProviderHosts().isPresent());
        assertEquals(List.of("api.openai.com", "bedrock.aws"), config.getAllowedProviderHosts().get());

        AiBridgeConfig emptyHosts = new AiBridgeConfig();
        setField(emptyHosts, "allowedProviderHosts", Optional.<List<String>>empty());
        assertTrue(emptyHosts.getAllowedProviderHosts().isEmpty());
    }

    private static void setField(AiBridgeConfig target, String fieldName, Object value) throws Exception {
        Field f = AiBridgeConfig.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
