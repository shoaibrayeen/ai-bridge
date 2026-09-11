package com.aibridge.service;

import com.aibridge.cache.CacheProvider;
import com.aibridge.config.RedisConfig;
import com.aibridge.exception.ConfigNotFoundException;
import com.aibridge.model.Feature;
import com.aibridge.model.LlmConfig;
import com.aibridge.repository.LlmConfigRepository;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
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
     * Mapper used only for the chain cache. {@code LlmConfig.credentialsEncrypted} is
     * {@code @JsonIgnore} so the entity can never leak ciphertext through an API response — but
     * the cache round-trips the entity through JSON, and dropping the field there meant every
     * cache <em>hit</em> produced configs whose credentials were null. First call fine, second
     * call broken, for five minutes. The mixin un-ignores the field for this mapper alone.
     */
    private ObjectMapper cacheMapper;

    @PostConstruct
    void initCacheMapper() {
        cacheMapper = objectMapper.copy().addMixIn(LlmConfig.class, IncludeCredentialsInCache.class);
    }

    private abstract static class IncludeCredentialsInCache {
        // @JsonProperty alone does not beat the entity's @JsonIgnore — the ignore marker must be
        // explicitly cancelled, then the property declared.
        @JsonIgnore(false)
        @JsonProperty
        @SuppressWarnings("unused")
        String credentialsEncrypted;
    }

    /**
     * Resolves the failover chain for the tenant and feature. Order from the repository is tenant primary,
     * tenant fallback, then global primary, global fallback.
     */
    public List<LlmConfig> resolveChain(String tenantId, String feature) {
        String cacheKey = RedisConfig.configCacheKey(tenantId, feature);
        String cached = cacheProvider.get(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            try {
                return cacheMapper.readValue(cached, LLM_CONFIG_LIST_TYPE);
            } catch (JsonProcessingException e) {
                cacheProvider.del(cacheKey);
            }
        }

        List<LlmConfig> fromDb = llmConfigRepository.findByTenantAndFeatureOrGlobal(tenantId, feature);
        if (fromDb == null || fromDb.isEmpty()) {
            throw new ConfigNotFoundException(tenantId, feature);
        }

        try {
            cacheProvider.setex(cacheKey, CONFIG_CACHE_TTL_SECONDS, cacheMapper.writeValueAsString(fromDb));
        } catch (JsonProcessingException ignored) {
            // Cache is best-effort; return DB result even if serialization fails.
        }

        return fromDb;
    }

    /**
     * Resolves the single config a gateway model name points at. Pinning a model selects exactly
     * one config, so the returned chain has no fallback links — failover is a property of
     * feature-based routing.
     *
     * <p>A tenant may address its own configs and the global ones. Naming another tenant's config
     * is indistinguishable from naming one that does not exist.
     */
    public List<LlmConfig> resolveByGatewayModelName(String tenantId, String gatewayModelName) {
        LlmConfig config = llmConfigRepository.findActiveByGatewayModelName(gatewayModelName);
        if (config == null || !isAddressableBy(tenantId, config)) {
            throw new ConfigNotFoundException(tenantId, "model=" + gatewayModelName);
        }
        return List.of(config);
    }

    private static boolean isAddressableBy(String tenantId, LlmConfig config) {
        String owner = config.getTenantId();
        return owner == null || owner.equals(tenantId);
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
