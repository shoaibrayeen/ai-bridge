package com.aibridge.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Single-JVM, non-distributed cache backed by {@link ConcurrentHashMap}.
 * Suitable for local development or single-instance deployments.
 */
public class InMemoryCacheProvider implements CacheProvider {

    private final ConcurrentHashMap<String, CacheEntry> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CounterState> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BucketState> buckets = new ConcurrentHashMap<>();

    @Override
    public String get(String key) {
        CacheEntry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            store.remove(key);
            return null;
        }
        return entry.value;
    }

    @Override
    public void setex(String key, long ttlSeconds, String value) {
        long expiresAt = System.currentTimeMillis() + ttlSeconds * 1000;
        store.put(key, new CacheEntry(value, expiresAt));
    }

    @Override
    public void del(String... keys) {
        for (String key : keys) {
            store.remove(key);
            counters.remove(key);
            buckets.remove(key);
        }
    }

    @Override
    public long incr(String key) {
        CounterState state = counters.computeIfAbsent(key, k -> new CounterState());
        return state.increment();
    }

    @Override
    public void decr(String key) {
        CounterState state = counters.get(key);
        if (state != null) {
            state.decrement();
        }
    }

    @Override
    public void expire(String key, int seconds) {
        CounterState state = counters.get(key);
        if (state != null) {
            state.setExpiry(System.currentTimeMillis() + seconds * 1000L);
        }
    }

    @Override
    public boolean tryAcquireToken(String bucketKey, int capacity, double refillRate, long nowMillis) {
        BucketState bucket = buckets.computeIfAbsent(bucketKey,
                k -> new BucketState(capacity, nowMillis));
        return bucket.tryAcquire(capacity, refillRate, nowMillis);
    }

    @Override
    public boolean tryTpmCheck(String bucketKey, int capacity, double refillRate, long nowMillis) {
        BucketState bucket = buckets.computeIfAbsent(bucketKey,
                k -> new BucketState(capacity, nowMillis));
        return bucket.tryCheck(capacity, refillRate, nowMillis);
    }

    @Override
    public void reconcileTpm(String bucketKey, double delta) {
        BucketState bucket = buckets.get(bucketKey);
        if (bucket != null) {
            bucket.adjustTokens(delta);
        }
    }

    @Override
    public boolean ping() {
        return true;
    }

    // --- Internal data structures ---

    private static final class CacheEntry {
        final String value;
        final long expiresAtMs;

        CacheEntry(String value, long expiresAtMs) {
            this.value = value;
            this.expiresAtMs = expiresAtMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMs;
        }
    }

    private static final class CounterState {
        private final AtomicLong value = new AtomicLong(0);
        private volatile long expiresAtMs = Long.MAX_VALUE;

        long increment() {
            if (System.currentTimeMillis() > expiresAtMs) {
                value.set(0);
                expiresAtMs = Long.MAX_VALUE;
            }
            return value.incrementAndGet();
        }

        void decrement() {
            value.decrementAndGet();
        }

        void setExpiry(long expiresAtMs) {
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static final class BucketState {
        private double tokens;
        private long lastRefillMs;

        BucketState(double initialTokens, long nowMs) {
            this.tokens = initialTokens;
            this.lastRefillMs = nowMs;
        }

        synchronized boolean tryAcquire(int capacity, double refillRate, long nowMs) {
            refill(capacity, refillRate, nowMs);
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        synchronized boolean tryCheck(int capacity, double refillRate, long nowMs) {
            refill(capacity, refillRate, nowMs);
            return tokens >= 1;
        }

        synchronized void adjustTokens(double delta) {
            tokens += delta;
        }

        private void refill(int capacity, double refillRate, long nowMs) {
            double elapsed = (nowMs - lastRefillMs) / 1000.0;
            tokens = Math.min(capacity, tokens + elapsed * refillRate);
            lastRefillMs = nowMs;
        }
    }
}
