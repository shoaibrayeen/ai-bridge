package com.aibridge.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.vertx.mutiny.redis.client.Command;
import io.vertx.mutiny.redis.client.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisCacheProviderTest {

    @Mock RedisDataSource redisDataSource;
    @Mock ValueCommands<String, String> valueCommands;
    @Mock KeyCommands<String> keyCommands;
    @Mock HashCommands<String, String, String> hashCommands;
    @Mock Response response;

    private RedisCacheProvider provider;

    @BeforeEach
    void setUp() {
        when(redisDataSource.value(String.class, String.class)).thenReturn(valueCommands);
        when(redisDataSource.key()).thenReturn(keyCommands);
        when(redisDataSource.hash(String.class)).thenReturn(hashCommands);
        provider = new RedisCacheProvider(redisDataSource);
    }

    @Test
    void get_delegatesToValueCommands() {
        when(valueCommands.get("k")).thenReturn("v");
        assertEquals("v", provider.get("k"));
    }

    @Test
    void get_whenNull_returnsNull() {
        when(valueCommands.get("k")).thenReturn(null);
        assertNull(provider.get("k"));
    }

    @Test
    void setex_delegatesToValueCommands() {
        provider.setex("k", 300, "v");
        verify(valueCommands).setex("k", 300, "v");
    }

    @Test
    void del_delegatesToKeyCommands() {
        provider.del("k1", "k2");
        verify(keyCommands).del("k1", "k2");
    }

    @Test
    void incr_delegatesToExecute() {
        when(response.toInteger()).thenReturn(5);
        when(redisDataSource.execute(eq(Command.INCR), anyString())).thenReturn(response);
        assertEquals(5, provider.incr("key"));
    }

    @Test
    void incr_whenNullResponse_returnsZero() {
        when(redisDataSource.execute(eq(Command.INCR), anyString())).thenReturn(null);
        assertEquals(0, provider.incr("key"));
    }

    @Test
    void decr_delegatesToExecute() {
        provider.decr("key");
        verify(redisDataSource).execute(Command.DECR, "key");
    }

    @Test
    void expire_delegatesToExecute() {
        provider.expire("key", 120);
        verify(redisDataSource).execute(Command.EXPIRE, "key", "120");
    }

    @Test
    void tryAcquireToken_whenGranted_returnsTrue() {
        when(response.toInteger()).thenReturn(1);
        when(redisDataSource.execute(eq(Command.EVAL), any(), any(), any(), any(), any(), any()))
                .thenReturn(response);
        assertTrue(provider.tryAcquireToken("bucket", 10, 10.0, 1000L));
    }

    @Test
    void tryAcquireToken_whenDenied_returnsFalse() {
        when(response.toInteger()).thenReturn(0);
        when(redisDataSource.execute(eq(Command.EVAL), any(), any(), any(), any(), any(), any()))
                .thenReturn(response);
        assertFalse(provider.tryAcquireToken("bucket", 10, 10.0, 1000L));
    }

    @Test
    void tryAcquireToken_whenNullResponse_returnsFalse() {
        when(redisDataSource.execute(eq(Command.EVAL), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);
        assertFalse(provider.tryAcquireToken("bucket", 10, 10.0, 1000L));
    }

    @Test
    void tryTpmCheck_whenGranted_returnsTrue() {
        when(response.toInteger()).thenReturn(1);
        when(redisDataSource.execute(eq(Command.EVAL), any(), any(), any(), any(), any(), any()))
                .thenReturn(response);
        assertTrue(provider.tryTpmCheck("tpm", 100, 1.67, 1000L));
    }

    @Test
    void tryTpmCheck_whenDenied_returnsFalse() {
        when(response.toInteger()).thenReturn(0);
        when(redisDataSource.execute(eq(Command.EVAL), any(), any(), any(), any(), any(), any()))
                .thenReturn(response);
        assertFalse(provider.tryTpmCheck("tpm", 100, 1.67, 1000L));
    }

    @Test
    void tryTpmCheck_whenNullResponse_returnsFalse() {
        when(redisDataSource.execute(eq(Command.EVAL), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);
        assertFalse(provider.tryTpmCheck("tpm", 100, 1.67, 1000L));
    }

    @Test
    void reconcileTpm_delegatesToHash() {
        provider.reconcileTpm("tpm-key", -42.0);
        verify(hashCommands).hincrbyfloat("tpm-key", "tokens", -42.0);
    }

    @Test
    void ping_whenPong_returnsTrue() {
        when(response.toString()).thenReturn("PONG");
        when(redisDataSource.execute(Command.PING)).thenReturn(response);
        assertTrue(provider.ping());
    }

    @Test
    void ping_whenPongLowerCase_returnsTrue() {
        when(response.toString()).thenReturn("pong");
        when(redisDataSource.execute(Command.PING)).thenReturn(response);
        assertTrue(provider.ping());
    }

    @Test
    void ping_whenNull_returnsFalse() {
        when(redisDataSource.execute(Command.PING)).thenReturn(null);
        assertFalse(provider.ping());
    }

    @Test
    void ping_whenNotPong_returnsFalse() {
        when(response.toString()).thenReturn("NOPE");
        when(redisDataSource.execute(Command.PING)).thenReturn(response);
        assertFalse(provider.ping());
    }
}
