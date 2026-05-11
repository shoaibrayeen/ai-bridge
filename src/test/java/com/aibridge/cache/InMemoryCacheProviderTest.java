package com.aibridge.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryCacheProviderTest {

    private InMemoryCacheProvider provider;

    @BeforeEach
    void setUp() {
        provider = new InMemoryCacheProvider();
    }

    @Test
    void get_nonExistentKey_returnsNull() {
        assertNull(provider.get("missing"));
    }

    @Test
    void setexAndGet_returnsStoredValue() {
        provider.setex("k1", 60, "val1");
        assertEquals("val1", provider.get("k1"));
    }

    @Test
    void get_afterExpiry_returnsNull() throws InterruptedException {
        provider.setex("k1", 1, "val1");
        assertEquals("val1", provider.get("k1"));
        Thread.sleep(1100);
        assertNull(provider.get("k1"));
    }

    @Test
    void del_removesKeys() {
        provider.setex("a", 60, "1");
        provider.setex("b", 60, "2");
        provider.del("a", "b");
        assertNull(provider.get("a"));
        assertNull(provider.get("b"));
    }

    @Test
    void incr_incrementsAndReturns() {
        assertEquals(1, provider.incr("counter"));
        assertEquals(2, provider.incr("counter"));
        assertEquals(3, provider.incr("counter"));
    }

    @Test
    void decr_decrementsCounter() {
        provider.incr("counter");
        provider.incr("counter");
        provider.decr("counter");
        assertEquals(2, provider.incr("counter"));
    }

    @Test
    void decr_onNonExistentKey_doesNothing() {
        provider.decr("nonexistent");
    }

    @Test
    void expire_setsExpiryOnCounter() throws InterruptedException {
        provider.incr("counter");
        provider.incr("counter");
        provider.expire("counter", 1);
        assertEquals(3, provider.incr("counter"));
        Thread.sleep(1100);
        assertEquals(1, provider.incr("counter"));
    }

    @Test
    void expire_onNonExistentKey_doesNothing() {
        provider.expire("nonexistent", 10);
    }

    @Test
    void tryAcquireToken_allowsUpToCapacity() {
        long now = System.currentTimeMillis();
        assertTrue(provider.tryAcquireToken("bucket", 2, 2.0, now));
        assertTrue(provider.tryAcquireToken("bucket", 2, 2.0, now));
        assertFalse(provider.tryAcquireToken("bucket", 2, 2.0, now));
    }

    @Test
    void tryAcquireToken_refillsOverTime() throws InterruptedException {
        long now = System.currentTimeMillis();
        assertTrue(provider.tryAcquireToken("bucket", 1, 1.0, now));
        assertFalse(provider.tryAcquireToken("bucket", 1, 1.0, now));
        Thread.sleep(1100);
        long later = System.currentTimeMillis();
        assertTrue(provider.tryAcquireToken("bucket", 1, 1.0, later));
    }

    @Test
    void tryTpmCheck_doesNotConsumeTokens() {
        long now = System.currentTimeMillis();
        assertTrue(provider.tryTpmCheck("tpm", 5, 5.0, now));
        assertTrue(provider.tryTpmCheck("tpm", 5, 5.0, now));
        assertTrue(provider.tryTpmCheck("tpm", 5, 5.0, now));
    }

    @Test
    void tryTpmCheck_respectsCapacityAfterReconciliation() {
        long now = System.currentTimeMillis();
        assertTrue(provider.tryTpmCheck("tpm", 10, 10.0, now));
        provider.reconcileTpm("tpm", -10);
        assertFalse(provider.tryTpmCheck("tpm", 10, 10.0, now));
    }

    @Test
    void reconcileTpm_adjustsTokens() {
        long now = System.currentTimeMillis();
        assertTrue(provider.tryAcquireToken("bucket", 2, 2.0, now));
        provider.reconcileTpm("bucket", -1.5);
        assertFalse(provider.tryAcquireToken("bucket", 2, 2.0, now));
    }

    @Test
    void reconcileTpm_onNonExistentBucket_doesNothing() {
        provider.reconcileTpm("nonexistent", -5);
    }

    @Test
    void ping_alwaysReturnsTrue() {
        assertTrue(provider.ping());
    }

    @Test
    void del_removesBucketsAndCounters() {
        provider.incr("key");
        long now = System.currentTimeMillis();
        provider.tryAcquireToken("key", 10, 10.0, now);
        provider.del("key");
        assertEquals(1, provider.incr("key"));
    }
}
