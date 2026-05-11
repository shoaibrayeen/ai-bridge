package com.aibridge.cache;

/**
 * Abstraction over the backing cache/store used for config caching,
 * auth-token caching, and distributed rate-pacing (token buckets).
 * Implementations: {@link InMemoryCacheProvider}, {@link RedisCacheProvider}.
 */
public interface CacheProvider {

    // --- Key-Value operations (config cache, auth-token cache) ---

    String get(String key);

    void setex(String key, long ttlSeconds, String value);

    void del(String... keys);

    // --- Atomic counter operations (queue-depth tracking) ---

    long incr(String key);

    void decr(String key);

    void expire(String key, int seconds);

    // --- Token-bucket rate-pacing ---

    boolean tryAcquireToken(String bucketKey, int capacity, double refillRate, long nowMillis);

    boolean tryTpmCheck(String bucketKey, int capacity, double refillRate, long nowMillis);

    void reconcileTpm(String bucketKey, double delta);

    // --- Health ---

    boolean ping();
}
