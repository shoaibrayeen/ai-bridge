package com.aibridge.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aibridge.model.enums.AuthType;
import com.aibridge.model.enums.ProviderName;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelGetterSetterTest {

    @Test
    void llmConfig_gettersAndSetters() {
        LlmConfig c = new LlmConfig();
        UUID id = UUID.randomUUID();
        LlmProvider p = new LlmProvider();
        List<String> stop = List.of("s");
        List<Feature> features = new ArrayList<>();
        Instant t1 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2024-01-02T00:00:00Z");

        c.setId(id);
        c.setTenantId("t1");
        c.setProvider(p);
        c.setModelName("mod");
        c.setEndpointUrl("https://x");
        c.setCredentialsEncrypted("enc");
        c.setRpsLimit(1);
        c.setRpmLimit(2);
        c.setTpmLimit(3);
        c.setDefaultTemperature(0.5);
        c.setDefaultMaxTokens(100);
        c.setDefaultTopP(0.8);
        c.setDefaultN(2);
        c.setDefaultStop(stop);
        c.setDefaultPresencePenalty(0.1);
        c.setDefaultFrequencyPenalty(0.2);
        c.setQueueTimeoutMs(4000);
        c.setFallback(true);
        c.setPriority(7);
        c.setExtraParams("{}");
        c.setActive(false);
        c.setFeatures(features);
        c.setCreatedAt(t1);
        c.setUpdatedAt(t2);

        assertEquals(id, c.getId());
        assertEquals("t1", c.getTenantId());
        assertSame(p, c.getProvider());
        assertEquals("mod", c.getModelName());
        assertEquals("https://x", c.getEndpointUrl());
        assertEquals("enc", c.getCredentialsEncrypted());
        assertEquals(1, c.getRpsLimit());
        assertEquals(2, c.getRpmLimit());
        assertEquals(3, c.getTpmLimit());
        assertEquals(0.5, c.getDefaultTemperature());
        assertEquals(100, c.getDefaultMaxTokens());
        assertEquals(0.8, c.getDefaultTopP());
        assertEquals(2, c.getDefaultN());
        assertSame(stop, c.getDefaultStop());
        assertEquals(0.1, c.getDefaultPresencePenalty());
        assertEquals(0.2, c.getDefaultFrequencyPenalty());
        assertEquals(4000, c.getQueueTimeoutMs());
        assertTrue(c.isFallback());
        assertEquals(7, c.getPriority());
        assertEquals("{}", c.getExtraParams());
        assertFalse(c.isActive());
        assertSame(features, c.getFeatures());
        assertEquals(t1, c.getCreatedAt());
        assertEquals(t2, c.getUpdatedAt());
    }

    @Test
    void llmProvider_gettersAndSetters() {
        LlmProvider p = new LlmProvider();
        UUID id = UUID.randomUUID();
        Instant c = Instant.parse("2024-05-01T00:00:00Z");
        Instant u = Instant.parse("2024-05-02T00:00:00Z");

        p.setId(id);
        p.setName(ProviderName.BEDROCK);
        p.setAuthType(AuthType.AWS_SIGV4);
        p.setAuthEndpoint("https://auth");
        p.setCreatedAt(c);
        p.setUpdatedAt(u);

        assertEquals(id, p.getId());
        assertEquals(ProviderName.BEDROCK, p.getName());
        assertEquals(AuthType.AWS_SIGV4, p.getAuthType());
        assertEquals("https://auth", p.getAuthEndpoint());
        assertEquals(c, p.getCreatedAt());
        assertEquals(u, p.getUpdatedAt());
    }

    @Test
    void feature_gettersAndSetters() {
        Feature f = new Feature();
        UUID id = UUID.randomUUID();
        LlmConfig cfg = new LlmConfig();

        f.setId(id);
        f.setLlmConfig(cfg);
        f.setFeature("my-feature");

        assertEquals(id, f.getId());
        assertSame(cfg, f.getLlmConfig());
        assertEquals("my-feature", f.getFeature());
    }

    @Test
    void llmConfig_prePersistAndPreUpdate_viaReflection() throws Exception {
        LlmConfig c = new LlmConfig();
        assertNull(c.getCreatedAt());
        assertNull(c.getUpdatedAt());

        Method onCreate = LlmConfig.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        onCreate.invoke(c);

        assertNotNull(c.getCreatedAt());
        assertNotNull(c.getUpdatedAt());
        assertEquals(c.getCreatedAt(), c.getUpdatedAt());

        Instant beforeUpdate = c.getUpdatedAt();
        Thread.sleep(2);

        Method onUpdate = LlmConfig.class.getDeclaredMethod("onUpdate");
        onUpdate.setAccessible(true);
        onUpdate.invoke(c);

        assertEquals(beforeUpdate, c.getCreatedAt());
        assertTrue(!c.getUpdatedAt().isBefore(beforeUpdate));
        assertNotNull(c.getUpdatedAt());
    }

    @Test
    void llmProvider_prePersistAndPreUpdate_viaReflection() throws Exception {
        LlmProvider p = new LlmProvider();
        assertNull(p.getCreatedAt());
        assertNull(p.getUpdatedAt());

        Method onCreate = LlmProvider.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        onCreate.invoke(p);

        assertNotNull(p.getCreatedAt());
        assertNotNull(p.getUpdatedAt());
        assertEquals(p.getCreatedAt(), p.getUpdatedAt());

        Instant before = p.getUpdatedAt();
        Thread.sleep(2);

        Method onUpdate = LlmProvider.class.getDeclaredMethod("onUpdate");
        onUpdate.setAccessible(true);
        onUpdate.invoke(p);

        assertEquals(before, p.getCreatedAt());
        assertTrue(!p.getUpdatedAt().isBefore(before));
    }

    @Test
    void authType_enum_valuesAndValueOf() {
        AuthType[] values = AuthType.values();
        assertEquals(4, values.length);
        assertArrayEquals(
                new AuthType[] {AuthType.API_KEY, AuthType.IAM_TOKEN, AuthType.AWS_SIGV4, AuthType.OAUTH2},
                values);
        assertEquals(AuthType.API_KEY, AuthType.valueOf("API_KEY"));
        assertEquals(AuthType.IAM_TOKEN, AuthType.valueOf("IAM_TOKEN"));
        assertEquals(AuthType.AWS_SIGV4, AuthType.valueOf("AWS_SIGV4"));
        assertEquals(AuthType.OAUTH2, AuthType.valueOf("OAUTH2"));
    }

    @Test
    void providerName_enum_valuesAndValueOf() {
        ProviderName[] values = ProviderName.values();
        assertEquals(5, values.length);
        assertArrayEquals(
                new ProviderName[] {
                    ProviderName.OPENAI,
                    ProviderName.BEDROCK,
                    ProviderName.WATSONX,
                    ProviderName.CEREBRAS,
                    ProviderName.CLAUDE
                },
                values);
        assertEquals(ProviderName.OPENAI, ProviderName.valueOf("OPENAI"));
        assertEquals(ProviderName.BEDROCK, ProviderName.valueOf("BEDROCK"));
        assertEquals(ProviderName.WATSONX, ProviderName.valueOf("WATSONX"));
        assertEquals(ProviderName.CEREBRAS, ProviderName.valueOf("CEREBRAS"));
        assertEquals(ProviderName.CLAUDE, ProviderName.valueOf("CLAUDE"));
    }
}
