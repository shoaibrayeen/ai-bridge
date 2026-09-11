package com.aibridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibridge.cache.CacheProvider;
import com.aibridge.config.RedisConfig;
import com.aibridge.exception.ConfigNotFoundException;
import com.aibridge.model.Feature;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.LlmProvider;
import com.aibridge.model.enums.ProviderName;
import com.aibridge.repository.LlmConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConfigResolverServiceTest {

    @Mock
    LlmConfigRepository llmConfigRepository;

    @Mock
    CacheProvider cacheProvider;

    @InjectMocks
    ConfigResolverService configResolverService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void wireMapper() throws Exception {
        Field om = ConfigResolverService.class.getDeclaredField("objectMapper");
        om.setAccessible(true);
        om.set(configResolverService, objectMapper);
        // Production wiring runs @PostConstruct; tests must too, or the cache mapper is null.
        java.lang.reflect.Method init =
                ConfigResolverService.class.getDeclaredMethod("initCacheMapper");
        init.setAccessible(true);
        init.invoke(configResolverService);
    }

    /**
     * Regression: the entity marks credentialsEncrypted as @JsonIgnore, and the chain cache
     * round-trips entities through JSON. With the default mapper the first (cache-miss) call
     * worked and every cache HIT for the next five minutes produced configs whose credentials
     * were null. This test does the real round-trip: capture what resolveChain stores, feed it
     * back as a hit, and require the credentials to survive.
     */
    @Test
    void cacheRoundTrip_preservesEncryptedCredentials() {
        String tenant = "t1";
        String feature = "chat";
        String cacheKey = RedisConfig.configCacheKey(tenant, feature);

        LlmConfig cfg = sampleConfig();
        cfg.setCredentialsEncrypted("ciphertext-base64");
        when(cacheProvider.get(cacheKey)).thenReturn(null);
        when(llmConfigRepository.findByTenantAndFeatureOrGlobal(tenant, feature))
                .thenReturn(List.of(cfg));

        configResolverService.resolveChain(tenant, feature);

        org.mockito.ArgumentCaptor<String> stored =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(cacheProvider).setex(eq(cacheKey), anyLong(), stored.capture());

        // Second call: a cache hit served from exactly the JSON the first call wrote.
        when(cacheProvider.get(cacheKey)).thenReturn(stored.getValue());

        List<LlmConfig> fromCache = configResolverService.resolveChain(tenant, feature);

        assertEquals("ciphertext-base64", fromCache.get(0).getCredentialsEncrypted(),
                "credentials must survive the cache round-trip or every cache hit fails to decrypt");
    }

    @Test
    void cacheRoundTrip_stillHidesCredentialsFromTheDefaultMapper() throws Exception {
        // The un-ignore is scoped to the cache mapper; API-facing serialization stays safe.
        LlmConfig cfg = sampleConfig();
        cfg.setCredentialsEncrypted("ciphertext-base64");
        String apiJson = objectMapper.writeValueAsString(cfg);
        org.junit.jupiter.api.Assertions.assertFalse(apiJson.contains("ciphertext-base64"),
                "default serialization must never carry ciphertext: " + apiJson);
    }

    @Test
    void cacheHit_returnsDeserializedConfigsWithoutDbCall() throws Exception {
        String tenant = "t1";
        String feature = "chat";
        String cacheKey = RedisConfig.configCacheKey(tenant, feature);

        LlmConfig cfg = sampleConfig();
        String cachedJson = objectMapper.writeValueAsString(List.of(cfg));
        when(cacheProvider.get(cacheKey)).thenReturn(cachedJson);

        List<LlmConfig> resolved = configResolverService.resolveChain(tenant, feature);

        assertEquals(1, resolved.size());
        assertEquals(cfg.getId(), resolved.get(0).getId());
        assertEquals(cfg.getModelName(), resolved.get(0).getModelName());
        verify(llmConfigRepository, never()).findByTenantAndFeatureOrGlobal(any(), any());
        verify(cacheProvider, never()).setex(anyString(), anyLong(), anyString());
    }

    @Test
    void cacheMiss_loadsFromDbAndCachesSerializedResult() throws Exception {
        String tenant = "t1";
        String feature = "chat";
        String cacheKey = RedisConfig.configCacheKey(tenant, feature);

        when(cacheProvider.get(cacheKey)).thenReturn(null);

        LlmConfig cfg = sampleConfig();
        when(llmConfigRepository.findByTenantAndFeatureOrGlobal(tenant, feature)).thenReturn(List.of(cfg));

        List<LlmConfig> resolved = configResolverService.resolveChain(tenant, feature);

        assertEquals(1, resolved.size());
        // Assert the stored payload semantically rather than byte-for-byte: the cache mapper
        // deliberately serializes more than the API-facing mapper (credentials included).
        org.mockito.ArgumentCaptor<String> stored = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(cacheProvider).setex(eq(cacheKey), eq(300L), stored.capture());
        org.junit.jupiter.api.Assertions.assertTrue(
                stored.getValue().contains(cfg.getModelName()), stored.getValue());
    }

    @Test
    void emptyDbResult_throwsConfigNotFoundException() {
        when(cacheProvider.get(RedisConfig.configCacheKey("t", "f"))).thenReturn(null);
        when(llmConfigRepository.findByTenantAndFeatureOrGlobal("t", "f")).thenReturn(List.of());

        assertThrows(ConfigNotFoundException.class, () -> configResolverService.resolveChain("t", "f"));
    }

    @Test
    void nullDbResult_throwsConfigNotFoundException() {
        when(cacheProvider.get(RedisConfig.configCacheKey("t", "f"))).thenReturn(null);
        when(llmConfigRepository.findByTenantAndFeatureOrGlobal("t", "f")).thenReturn(null);

        assertThrows(ConfigNotFoundException.class, () -> configResolverService.resolveChain("t", "f"));
    }

    @Test
    void cacheMiss_whenSerializationFails_stillReturnsDbResultWithoutCaching() throws Exception {
        String tenant = "t1";
        String feature = "chat";
        String cacheKey = RedisConfig.configCacheKey(tenant, feature);

        ObjectMapper spyMapper = Mockito.spy(objectMapper);
        // The cache path now serializes through the dedicated cache mapper, so the failure has to
        // be injected there — the plain objectMapper field is no longer on that code path.
        Field om = ConfigResolverService.class.getDeclaredField("cacheMapper");
        om.setAccessible(true);
        Object previous = om.get(configResolverService);
        om.set(configResolverService, spyMapper);
        try {
            Mockito.doThrow(new JsonProcessingException("cannot serialize") {})
                    .when(spyMapper)
                    .writeValueAsString(any());

            when(cacheProvider.get(cacheKey)).thenReturn(null);
            LlmConfig cfg = sampleConfig();
            when(llmConfigRepository.findByTenantAndFeatureOrGlobal(tenant, feature))
                    .thenReturn(List.of(cfg));

            List<LlmConfig> resolved = configResolverService.resolveChain(tenant, feature);

            assertEquals(1, resolved.size());
            verify(cacheProvider, never()).setex(anyString(), anyLong(), anyString());
        } finally {
            om.set(configResolverService, previous);
        }
    }

    @Test
    void invalidateCache_deletesTenantAndGlobalCacheKeys() {
        String tenant = "acme";
        String feature = "summarize";

        configResolverService.invalidateCache(tenant, feature);

        verify(cacheProvider)
                .del(
                        RedisConfig.configCacheKey(tenant, feature),
                        RedisConfig.configCacheKey(null, feature));
    }

    @Test
    void corruptedCacheJson_deletesKeyAndReturnsFromDb() throws Exception {
        String tenant = "t1";
        String feature = "chat";
        String cacheKey = RedisConfig.configCacheKey(tenant, feature);

        when(cacheProvider.get(cacheKey)).thenReturn("not valid json");

        LlmConfig cfg = sampleConfig();
        when(llmConfigRepository.findByTenantAndFeatureOrGlobal(tenant, feature)).thenReturn(List.of(cfg));

        List<LlmConfig> resolved = configResolverService.resolveChain(tenant, feature);

        assertEquals(1, resolved.size());
        verify(cacheProvider).del(cacheKey);
        verify(llmConfigRepository).findByTenantAndFeatureOrGlobal(tenant, feature);
        // Assert the stored payload semantically rather than byte-for-byte: the cache mapper
        // deliberately serializes more than the API-facing mapper (credentials included).
        org.mockito.ArgumentCaptor<String> stored = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(cacheProvider).setex(eq(cacheKey), eq(300L), stored.capture());
        org.junit.jupiter.api.Assertions.assertTrue(
                stored.getValue().contains(cfg.getModelName()), stored.getValue());
    }

    @Test
    void invalidateCacheForConfig_withFeatures_deletesKeys() {
        LlmConfig cfg = sampleConfig();
        cfg.setTenantId("acme");

        Feature f1 = new Feature();
        f1.setFeature("chat");
        Feature f2 = new Feature();
        f2.setFeature("summarize");
        cfg.setFeatures(List.of(f1, f2));

        configResolverService.invalidateCacheForConfig(cfg);

        verify(cacheProvider)
                .del(
                        RedisConfig.configCacheKey("acme", "chat"),
                        RedisConfig.configCacheKey(null, "chat"));
        verify(cacheProvider)
                .del(
                        RedisConfig.configCacheKey("acme", "summarize"),
                        RedisConfig.configCacheKey(null, "summarize"));
    }

    @Test
    void invalidateCacheForConfig_withNullFeatures_doesNothing() {
        LlmConfig cfg = sampleConfig();
        cfg.setFeatures(null);

        configResolverService.invalidateCacheForConfig(cfg);

        verify(cacheProvider, never()).del(any(), any());
    }

    @Test
    void invalidateCacheForConfig_withNullFeatureName_skipsNull() {
        LlmConfig cfg = sampleConfig();
        cfg.setTenantId("t1");

        Feature nullName = new Feature();
        Feature ok = new Feature();
        ok.setFeature("allowed");
        cfg.setFeatures(new ArrayList<>(List.of(nullName, ok)));

        configResolverService.invalidateCacheForConfig(cfg);

        verify(cacheProvider, times(1))
                .del(
                        RedisConfig.configCacheKey("t1", "allowed"),
                        RedisConfig.configCacheKey(null, "allowed"));
    }

    private static LlmConfig sampleConfig() {
        LlmProvider provider = new LlmProvider();
        provider.setName(ProviderName.OPENAI);
        LlmConfig cfg = new LlmConfig();
        cfg.setId(UUID.randomUUID());
        cfg.setModelName("gpt-test");
        cfg.setProvider(provider);
        return cfg;
    }

    // =========================================================================
    // Gateway model name resolution — DAO mocked.
    // =========================================================================

    @Test
    void resolveByGatewayModelName_returnsSingleConfigChain() {
        LlmConfig cfg = gatewayConfig("acme", "ai-bridge-2-claude-sonnet-6");
        when(llmConfigRepository.findActiveByGatewayModelName("ai-bridge-2-claude-sonnet-6"))
                .thenReturn(cfg);

        List<LlmConfig> chain =
                configResolverService.resolveByGatewayModelName("acme", "ai-bridge-2-claude-sonnet-6");

        // Pinning a model selects exactly one config — failover belongs to feature routing.
        assertEquals(1, chain.size());
        assertSame(cfg, chain.get(0));
    }

    @Test
    void resolveByGatewayModelName_globalConfigIsAddressableByAnyTenant() {
        LlmConfig cfg = gatewayConfig(null, "ai-bridge-1-gpt-4o");
        when(llmConfigRepository.findActiveByGatewayModelName("ai-bridge-1-gpt-4o")).thenReturn(cfg);

        assertEquals(1, configResolverService.resolveByGatewayModelName("acme", "ai-bridge-1-gpt-4o").size());
        assertEquals(1, configResolverService.resolveByGatewayModelName(null, "ai-bridge-1-gpt-4o").size());
    }

    @Test
    void resolveByGatewayModelName_anotherTenantsConfigIsNotAddressable() {
        LlmConfig cfg = gatewayConfig("other", "ai-bridge-1-gpt-4o");
        when(llmConfigRepository.findActiveByGatewayModelName("ai-bridge-1-gpt-4o")).thenReturn(cfg);

        assertThrows(
                ConfigNotFoundException.class,
                () -> configResolverService.resolveByGatewayModelName("acme", "ai-bridge-1-gpt-4o"));
    }

    @Test
    void resolveByGatewayModelName_tenantScopedConfigIsNotAddressableWithoutATenant() {
        LlmConfig cfg = gatewayConfig("acme", "ai-bridge-1-gpt-4o");
        when(llmConfigRepository.findActiveByGatewayModelName("ai-bridge-1-gpt-4o")).thenReturn(cfg);

        assertThrows(
                ConfigNotFoundException.class,
                () -> configResolverService.resolveByGatewayModelName(null, "ai-bridge-1-gpt-4o"));
    }

    @Test
    void resolveByGatewayModelName_unknownNameThrows() {
        when(llmConfigRepository.findActiveByGatewayModelName("ai-bridge-9-nope")).thenReturn(null);

        assertThrows(
                ConfigNotFoundException.class,
                () -> configResolverService.resolveByGatewayModelName("acme", "ai-bridge-9-nope"));
    }

    @Test
    void resolveByGatewayModelName_doesNotTouchTheConfigCache() {
        LlmConfig cfg = gatewayConfig("acme", "ai-bridge-1-gpt-4o");
        when(llmConfigRepository.findActiveByGatewayModelName("ai-bridge-1-gpt-4o")).thenReturn(cfg);

        configResolverService.resolveByGatewayModelName("acme", "ai-bridge-1-gpt-4o");

        // The 5-minute chain cache is keyed by tenant+feature; a pinned lookup must not read
        // or write it, or a stale chain could shadow a freshly edited config.
        verify(cacheProvider, never()).get(anyString());
        verify(cacheProvider, never()).setex(anyString(), anyLong(), anyString());
    }

    private static LlmConfig gatewayConfig(String tenantId, String gatewayModelName) {
        LlmConfig c = new LlmConfig();
        c.setId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setGatewayModelName(gatewayModelName);
        c.setActive(true);
        return c;
    }
}
