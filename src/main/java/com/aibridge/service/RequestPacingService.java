package com.aibridge.service;

import com.aibridge.cache.CacheProvider;
import com.aibridge.config.AiBridgeConfig;
import com.aibridge.config.RedisConfig;
import com.aibridge.exception.QueueTimeoutException;
import com.aibridge.model.LlmConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RequestPacingService {

    private final CacheProvider cacheProvider;
    private final AiBridgeConfig aiBridgeConfig;

    @Inject
    public RequestPacingService(CacheProvider cacheProvider, AiBridgeConfig aiBridgeConfig) {
        this.cacheProvider = cacheProvider;
        this.aiBridgeConfig = aiBridgeConfig;
    }

    public void acquireSlot(LlmConfig config) {
        if (config.getRpsLimit() == null
                && config.getRpmLimit() == null
                && config.getTpmLimit() == null) {
            return;
        }

        String configId = config.getId().toString();
        String depthKey = RedisConfig.KEY_PREFIX + "acquire_depth:" + configId;

        try {
            long depth = cacheProvider.incr(depthKey);
            cacheProvider.expire(depthKey, aiBridgeConfig.getQueueTimeoutMs() / 1000 + 60);
            if (depth > aiBridgeConfig.getQueueMaxDepth()) {
                throw new QueueTimeoutException(configId);
            }

            long deadline = System.currentTimeMillis() + config.getQueueTimeoutMs();

            while (System.currentTimeMillis() <= deadline) {
                if (tryAcquireAll(config)) {
                    return;
                }
                Thread.sleep(aiBridgeConfig.getQueuePollIntervalMs());
            }
            throw new QueueTimeoutException(configId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QueueTimeoutException(configId, e);
        } finally {
            cacheProvider.decr(depthKey);
        }
    }

    public void reconcileTokens(LlmConfig config, int actualTokens) {
        if (config.getTpmLimit() == null || actualTokens <= 0) {
            return;
        }
        String key = RedisConfig.tokenBucketKey(config.getId().toString(), "tpm");
        cacheProvider.reconcileTpm(key, -actualTokens);
    }

    private boolean tryAcquireAll(LlmConfig config) {
        String id = config.getId().toString();
        long now = System.currentTimeMillis();

        if (config.getRpsLimit() != null) {
            String key = RedisConfig.tokenBucketKey(id, "rps");
            if (!cacheProvider.tryAcquireToken(key, config.getRpsLimit(), config.getRpsLimit(), now)) {
                return false;
            }
        }
        if (config.getRpmLimit() != null) {
            String key = RedisConfig.tokenBucketKey(id, "rpm");
            if (!cacheProvider.tryAcquireToken(key, config.getRpmLimit(), config.getRpmLimit() / 60.0, now)) {
                return false;
            }
        }
        if (config.getTpmLimit() != null) {
            String key = RedisConfig.tokenBucketKey(id, "tpm");
            if (!cacheProvider.tryTpmCheck(key, config.getTpmLimit(), config.getTpmLimit() / 60.0, now)) {
                return false;
            }
        }
        return true;
    }
}
