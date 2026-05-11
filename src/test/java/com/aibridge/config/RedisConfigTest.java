package com.aibridge.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class RedisConfigTest {

    @Test
    void keyPrefix_isExpectedValue() {
        assertEquals("aibridge:", RedisConfig.KEY_PREFIX);
    }

    @Test
    void configCacheKey_withTenant_buildsKey() {
        assertEquals(
                "aibridge:llm_config:acme:chat",
                RedisConfig.configCacheKey("acme", "chat"));
    }

    @Test
    void configCacheKey_withNullTenant_usesGlobalPlaceholder() {
        assertEquals(
                "aibridge:llm_config:__GLOBAL__:embed",
                RedisConfig.configCacheKey(null, "embed"));
    }

    @Test
    void authTokenKey_buildsKey() {
        assertEquals(
                "aibridge:auth_token:prov-1:cfg-2",
                RedisConfig.authTokenKey("prov-1", "cfg-2"));
    }

    @Test
    void tokenBucketKey_buildsKey() {
        assertEquals(
                "aibridge:tokenbucket:uuid-abc:rps",
                RedisConfig.tokenBucketKey("uuid-abc", "rps"));
    }

    @Test
    void configCacheKey_withEmptyStringTenant_preservesEmptyTenantSegment() {
        assertEquals("aibridge:llm_config::feat", RedisConfig.configCacheKey("", "feat"));
    }

    @Test
    void configCacheKey_withEmptyFeature_preservesEmptyFeatureSegment() {
        assertEquals("aibridge:llm_config:tenant:", RedisConfig.configCacheKey("tenant", ""));
    }

    @Test
    void authTokenKey_withEmptySegments_buildsKey() {
        assertEquals("aibridge:auth_token::", RedisConfig.authTokenKey("", ""));
    }

    @Test
    void tokenBucketKey_withEmptySegments_buildsKey() {
        assertEquals("aibridge:tokenbucket::dim", RedisConfig.tokenBucketKey("", "dim"));
    }

    @Test
    void constructor_instantiates() {
        RedisConfig config = new RedisConfig();
        assertNotNull(config);
    }
}
