package com.aibridge.config;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RedisConfig {

    public static final String KEY_PREFIX = "aibridge:";

    private static final String GLOBAL_TENANT = "__GLOBAL__";

    public static String configCacheKey(String tenantId, String feature) {
        String resolvedTenant = tenantId == null ? GLOBAL_TENANT : tenantId;
        return KEY_PREFIX + "llm_config:" + resolvedTenant + ":" + feature;
    }

    public static String authTokenKey(String providerId, String configId) {
        return KEY_PREFIX + "auth_token:" + providerId + ":" + configId;
    }

    public static String tokenBucketKey(String configId, String dimension) {
        return KEY_PREFIX + "tokenbucket:" + configId + ":" + dimension;
    }
}
