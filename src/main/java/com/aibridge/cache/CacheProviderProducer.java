package com.aibridge.cache;

import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * CDI producer that selects the {@link CacheProvider} implementation based on
 * the {@code cache.mode} configuration property.
 * <ul>
 *   <li>{@code cache.mode=redis} &rarr; {@link RedisCacheProvider}</li>
 *   <li>Empty or {@code in-memory} (default) &rarr; {@link InMemoryCacheProvider}</li>
 * </ul>
 */
@ApplicationScoped
public class CacheProviderProducer {

    @ConfigProperty(name = "cache.mode", defaultValue = "in-memory")
    String cacheMode;

    @Inject
    Instance<RedisDataSource> redisDataSourceInstance;

    @Produces
    @ApplicationScoped
    CacheProvider produce() {
        if ("redis".equalsIgnoreCase(cacheMode)) {
            return new RedisCacheProvider(redisDataSourceInstance.get());
        }
        return new InMemoryCacheProvider();
    }
}
