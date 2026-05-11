package com.aibridge.dto.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aibridge.model.Feature;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.LlmProvider;
import com.aibridge.model.enums.ProviderName;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LlmConfigResponseTest {

    @Test
    void from_mapsAllFields_andMasksCredentials() {
        UUID id = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        Instant created = Instant.parse("2024-01-01T00:00:00Z");
        Instant updated = Instant.parse("2024-01-02T00:00:00Z");

        LlmProvider provider = new LlmProvider();
        provider.setId(providerId);
        provider.setName(ProviderName.OPENAI);

        Feature f1 = new Feature();
        f1.setFeature("chat");
        Feature f2 = new Feature();
        f2.setFeature("embed");

        LlmConfig entity = new LlmConfig();
        entity.setId(id);
        entity.setTenantId("tenant-a");
        entity.setProvider(provider);
        entity.setModelName("m1");
        entity.setEndpointUrl("https://api.example/v1");
        entity.setCredentialsEncrypted("secret-cipher");
        entity.setRpsLimit(10);
        entity.setRpmLimit(100);
        entity.setTpmLimit(1000);
        entity.setDefaultTemperature(0.7);
        entity.setDefaultMaxTokens(512);
        entity.setDefaultTopP(0.9);
        entity.setDefaultN(1);
        entity.setDefaultStop(List.of("STOP"));
        entity.setDefaultPresencePenalty(0.1);
        entity.setDefaultFrequencyPenalty(0.2);
        entity.setQueueTimeoutMs(3000);
        entity.setFallback(true);
        entity.setPriority(5);
        entity.setExtraParams("{\"k\":\"v\"}");
        entity.setActive(false);
        entity.setFeatures(new ArrayList<>(List.of(f1, f2)));
        entity.setCreatedAt(created);
        entity.setUpdatedAt(updated);

        LlmConfigResponse r = LlmConfigResponse.from(entity);

        assertEquals(id, r.getId());
        assertEquals("tenant-a", r.getTenantId());
        assertEquals(providerId, r.getProviderId());
        assertEquals("m1", r.getModelName());
        assertEquals("https://api.example/v1", r.getEndpointUrl());
        assertEquals("****", r.getCredentials());
        assertEquals(10, r.getRpsLimit());
        assertEquals(100, r.getRpmLimit());
        assertEquals(1000, r.getTpmLimit());
        assertEquals(0.7, r.getDefaultTemperature());
        assertEquals(512, r.getDefaultMaxTokens());
        assertEquals(0.9, r.getDefaultTopP());
        assertEquals(1, r.getDefaultN());
        assertEquals(List.of("STOP"), r.getDefaultStop());
        assertEquals(0.1, r.getDefaultPresencePenalty());
        assertEquals(0.2, r.getDefaultFrequencyPenalty());
        assertEquals(3000, r.getQueueTimeoutMs());
        assertTrue(r.getIsFallback());
        assertEquals(5, r.getPriority());
        assertEquals("{\"k\":\"v\"}", r.getExtraParams());
        assertEquals(List.of("chat", "embed"), r.getFeatures());
        assertFalse(r.getIsActive());
        assertEquals(created, r.getCreatedAt());
        assertEquals(updated, r.getUpdatedAt());
        assertEquals(ProviderName.OPENAI.name(), r.getProviderName());
    }

    @Test
    void from_nullProvider_setsProviderIdAndNameNull() {
        LlmConfig entity = new LlmConfig();
        entity.setId(UUID.randomUUID());
        entity.setTenantId("t");
        entity.setProvider(null);
        entity.setModelName("m");
        entity.setEndpointUrl("https://x");
        entity.setFeatures(null);

        LlmConfigResponse r = LlmConfigResponse.from(entity);

        assertNull(r.getProviderId());
        assertNull(r.getProviderName());
        assertEquals("****", r.getCredentials());
        assertNotNull(r.getFeatures());
        assertTrue(r.getFeatures().isEmpty());
    }

    @Test
    void from_providerWithNullName_mapsProviderIdButNullProviderName() {
        LlmProvider provider = new LlmProvider();
        provider.setId(UUID.randomUUID());
        provider.setName(null);

        LlmConfig entity = new LlmConfig();
        entity.setProvider(provider);
        entity.setFeatures(List.of());

        LlmConfigResponse r = LlmConfigResponse.from(entity);

        assertEquals(provider.getId(), r.getProviderId());
        assertNull(r.getProviderName());
    }

    @Test
    void gettersAndSetters_roundTrip() {
        LlmConfigResponse r = new LlmConfigResponse();
        UUID id = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        Instant now = Instant.now();
        List<String> stops = List.of("a");
        List<String> feats = List.of("f");

        r.setId(id);
        r.setTenantId("tid");
        r.setProviderId(pid);
        r.setModelName("mod");
        r.setEndpointUrl("url");
        r.setCredentials("c");
        r.setRpsLimit(1);
        r.setRpmLimit(2);
        r.setTpmLimit(3);
        r.setDefaultTemperature(0.5);
        r.setDefaultMaxTokens(100);
        r.setDefaultTopP(0.8);
        r.setDefaultN(2);
        r.setDefaultStop(stops);
        r.setDefaultPresencePenalty(0.3);
        r.setDefaultFrequencyPenalty(0.4);
        r.setQueueTimeoutMs(999);
        r.setIsFallback(true);
        r.setPriority(7);
        r.setExtraParams("{}");
        r.setFeatures(feats);
        r.setIsActive(true);
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        r.setProviderName("BEDROCK");

        assertEquals(id, r.getId());
        assertEquals("tid", r.getTenantId());
        assertEquals(pid, r.getProviderId());
        assertEquals("mod", r.getModelName());
        assertEquals("url", r.getEndpointUrl());
        assertEquals("c", r.getCredentials());
        assertEquals(1, r.getRpsLimit());
        assertEquals(2, r.getRpmLimit());
        assertEquals(3, r.getTpmLimit());
        assertEquals(0.5, r.getDefaultTemperature());
        assertEquals(100, r.getDefaultMaxTokens());
        assertEquals(0.8, r.getDefaultTopP());
        assertEquals(2, r.getDefaultN());
        assertEquals(stops, r.getDefaultStop());
        assertEquals(0.3, r.getDefaultPresencePenalty());
        assertEquals(0.4, r.getDefaultFrequencyPenalty());
        assertEquals(999, r.getQueueTimeoutMs());
        assertTrue(r.getIsFallback());
        assertEquals(7, r.getPriority());
        assertEquals("{}", r.getExtraParams());
        assertEquals(feats, r.getFeatures());
        assertTrue(r.getIsActive());
        assertEquals(now, r.getCreatedAt());
        assertEquals(now, r.getUpdatedAt());
        assertEquals("BEDROCK", r.getProviderName());
    }
}
