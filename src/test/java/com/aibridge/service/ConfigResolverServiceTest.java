package com.aibridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        verify(cacheProvider)
                .setex(eq(cacheKey), eq(300L), eq(objectMapper.writeValueAsString(List.of(cfg))));
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
        Field om = ConfigResolverService.class.getDeclaredField("objectMapper");
        om.setAccessible(true);
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
            om.set(configResolverService, objectMapper);
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
        verify(cacheProvider)
                .setex(eq(cacheKey), eq(300L), eq(objectMapper.writeValueAsString(List.of(cfg))));
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
}
