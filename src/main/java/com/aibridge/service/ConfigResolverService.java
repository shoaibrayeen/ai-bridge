package com.aibridge.service;

import com.aibridge.cache.CacheProvider;
import com.aibridge.config.RedisConfig;
import com.aibridge.exception.ConfigNotFoundException;
import com.aibridge.model.Feature;
import com.aibridge.model.LlmConfig;
import com.aibridge.repository.LlmConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class ConfigResolverService {

    private static final int CONFIG_CACHE_TTL_SECONDS = 300;

    private static final TypeReference<List<LlmConfig>> LLM_CONFIG_LIST_TYPE =
            new TypeReference<>() {};

    @Inject
    LlmConfigRepository llmConfigRepository;

    @Inject
    CacheProvider cacheProvider;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Resolves the failover chain for the tenant and feature. Order from the repository is tenant primary,
     * tenant fallback, then global primary, global fallback.
     */
    public List<LlmConfig> resolveChain(String tenantId, String feature) {
        String cacheKey = RedisConfig.configCacheKey(tenantId, feature);
        String cached = cacheProvider.get(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            try {
                return objectMapper.readValue(cached, LLM_CONFIG_LIST_TYPE);
            } catch (JsonProcessingException e) {
                cacheProvider.del(cacheKey);
            }
        }

        List<LlmConfig> fromDb = llmConfigRepository.findByTenantAndFeatureOrGlobal(tenantId, feature);
        if (fromDb == null || fromDb.isEmpty()) {
            throw new ConfigNotFoundException(tenantId, feature);
        }

        try {
            cacheProvider.setex(cacheKey, CONFIG_CACHE_TTL_SECONDS, objectMapper.writeValueAsString(fromDb));
        } catch (JsonProcessingException ignored) {
            // Cache is best-effort; return DB result even if serialization fails.
        }

        return fromDb;
    }

    public void invalidateCache(String tenantId, String feature) {
        cacheProvider.del(RedisConfig.configCacheKey(tenantId, feature), RedisConfig.configCacheKey(null, feature));
    }

    public void invalidateCacheForConfig(LlmConfig config) {
        if (config.getFeatures() == null) {
            return;
        }
        String tenantId = config.getTenantId();
        for (Feature featureEntity : config.getFeatures()) {
            if (featureEntity.getFeature() != null) {
                invalidateCache(tenantId, featureEntity.getFeature());
            }
        }
    }
}
