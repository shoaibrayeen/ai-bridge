package com.aibridge.cache;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.vertx.mutiny.redis.client.Command;
import io.vertx.mutiny.redis.client.Response;

/**
 * Redis-backed {@link CacheProvider}. Token-bucket pacing uses server-side Lua
 * scripts for atomicity across distributed instances.
 */
public class RedisCacheProvider implements CacheProvider {

    private static final String TOKEN_BUCKET_SCRIPT =
            """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local data = redis.call('HMGET', key, 'tokens', 'last_refill')
            local tokens = tonumber(data[1]) or capacity
            local last_refill = tonumber(data[2]) or now
            local elapsed = (now - last_refill) / 1000.0
            local refill = math.min(capacity, tokens + (elapsed * refill_rate))
            if refill >= 1 then
                redis.call('HMSET', key, 'tokens', refill - 1, 'last_refill', now)
                redis.call('EXPIRE', key, 120)
                return 1
            else
                redis.call('HMSET', key, 'tokens', refill, 'last_refill', now)
                redis.call('EXPIRE', key, 120)
                return 0
            end
            """;

    private static final String TPM_CHECK_SCRIPT =
            """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local data = redis.call('HMGET', key, 'tokens', 'last_refill')
            local tokens = tonumber(data[1]) or capacity
            local last_refill = tonumber(data[2]) or now
            local elapsed = (now - last_refill) / 1000.0
            local refill = math.min(capacity, tokens + (elapsed * refill_rate))
            if refill >= 1 then
                redis.call('HMSET', key, 'tokens', refill, 'last_refill', now)
                redis.call('EXPIRE', key, 120)
                return 1
            else
                redis.call('HMSET', key, 'tokens', refill, 'last_refill', now)
                redis.call('EXPIRE', key, 120)
                return 0
            end
            """;

    private final RedisDataSource redisDataSource;

    public RedisCacheProvider(RedisDataSource redisDataSource) {
        this.redisDataSource = redisDataSource;
    }

    @Override
    public String get(String key) {
        ValueCommands<String, String> values = redisDataSource.value(String.class, String.class);
        return values.get(key);
    }

    @Override
    public void setex(String key, long ttlSeconds, String value) {
        ValueCommands<String, String> values = redisDataSource.value(String.class, String.class);
        values.setex(key, ttlSeconds, value);
    }

    @Override
    public void del(String... keys) {
        redisDataSource.key().del(keys);
    }

    @Override
    public long incr(String key) {
        Response response = redisDataSource.execute(Command.INCR, key);
        return response != null ? response.toInteger() : 0;
    }

    @Override
    public void decr(String key) {
        redisDataSource.execute(Command.DECR, key);
    }

    @Override
    public void expire(String key, int seconds) {
        redisDataSource.execute(Command.EXPIRE, key, Integer.toString(seconds));
    }

    @Override
    public boolean tryAcquireToken(String bucketKey, int capacity, double refillRate, long nowMillis) {
        Response response = redisDataSource.execute(
                Command.EVAL,
                TOKEN_BUCKET_SCRIPT,
                "1",
                bucketKey,
                Integer.toString(capacity),
                Double.toString(refillRate),
                Long.toString(nowMillis));
        return response != null && response.toInteger() == 1;
    }

    @Override
    public boolean tryTpmCheck(String bucketKey, int capacity, double refillRate, long nowMillis) {
        Response response = redisDataSource.execute(
                Command.EVAL,
                TPM_CHECK_SCRIPT,
                "1",
                bucketKey,
                Integer.toString(capacity),
                Double.toString(refillRate),
                Long.toString(nowMillis));
        return response != null && response.toInteger() == 1;
    }

    @Override
    public void reconcileTpm(String bucketKey, double delta) {
        redisDataSource.hash(String.class).hincrbyfloat(bucketKey, "tokens", delta);
    }

    @Override
    public boolean ping() {
        Response pong = redisDataSource.execute(Command.PING);
        return pong != null && "PONG".equalsIgnoreCase(pong.toString());
    }
}
