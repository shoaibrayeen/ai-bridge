package com.aibridge.cache;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.enterprise.inject.Instance;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CacheProviderProducerTest {

    @Mock
    Instance<RedisDataSource> redisInstance;

    @Mock
    RedisDataSource redisDataSource;

    @Test
    void produce_withRedisMode_returnsRedisCacheProvider() throws Exception {
        when(redisInstance.get()).thenReturn(redisDataSource);

        CacheProviderProducer producer = newProducer("redis");
        CacheProvider result = producer.produce();

        assertInstanceOf(RedisCacheProvider.class, result);
        verify(redisInstance).get();
    }

    @Test
    void produce_withRedisModeUpperCase_returnsRedisCacheProvider() throws Exception {
        when(redisInstance.get()).thenReturn(redisDataSource);

        CacheProviderProducer producer = newProducer("REDIS");
        CacheProvider result = producer.produce();

        assertInstanceOf(RedisCacheProvider.class, result);
    }

    @Test
    void produce_withInMemoryMode_returnsInMemoryCacheProvider() throws Exception {
        CacheProviderProducer producer = newProducer("in-memory");
        CacheProvider result = producer.produce();

        assertInstanceOf(InMemoryCacheProvider.class, result);
        verify(redisInstance, never()).get();
    }

    @Test
    void produce_withEmptyMode_returnsInMemoryCacheProvider() throws Exception {
        CacheProviderProducer producer = newProducer("");
        CacheProvider result = producer.produce();

        assertInstanceOf(InMemoryCacheProvider.class, result);
    }

    @Test
    void produce_withNullLikeMode_returnsInMemoryCacheProvider() throws Exception {
        CacheProviderProducer producer = newProducer("anything-else");
        CacheProvider result = producer.produce();

        assertInstanceOf(InMemoryCacheProvider.class, result);
    }

    private CacheProviderProducer newProducer(String mode) throws Exception {
        CacheProviderProducer producer = new CacheProviderProducer();
        Field cacheModeField = CacheProviderProducer.class.getDeclaredField("cacheMode");
        cacheModeField.setAccessible(true);
        cacheModeField.set(producer, mode);
        Field redisField = CacheProviderProducer.class.getDeclaredField("redisDataSourceInstance");
        redisField.setAccessible(true);
        redisField.set(producer, redisInstance);
        return producer;
    }
}
