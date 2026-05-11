package com.aibridge.config;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class AiBridgeConfig {

    @ConfigProperty(name = "ui.required", defaultValue = "true")
    boolean uiRequired;

    @ConfigProperty(name = "aibridge.encryption.key")
    String encryptionKey;

    @ConfigProperty(name = "aibridge.queue.timeout-ms", defaultValue = "5000")
    int queueTimeoutMs;

    @ConfigProperty(name = "aibridge.queue.poll-interval-ms", defaultValue = "50")
    int queuePollIntervalMs;

    @ConfigProperty(name = "aibridge.queue.max-depth", defaultValue = "1000")
    int queueMaxDepth;

    @ConfigProperty(name = "cache.mode", defaultValue = "in-memory")
    String cacheMode;

    @ConfigProperty(name = "aibridge.auth.api-key", defaultValue = "CHANGE_ME_IN_PRODUCTION")
    String authApiKey;

    @ConfigProperty(name = "aibridge.auth.token-validity-minutes", defaultValue = "60")
    int authTokenValidityMinutes;

    @ConfigProperty(name = "aibridge.log.level", defaultValue = "INFO")
    String logLevel;

    @ConfigProperty(name = "aibridge.log.app-level", defaultValue = "DEBUG")
    String logAppLevel;

    @ConfigProperty(name = "aibridge.load-test.max-parallel", defaultValue = "500")
    int loadTestMaxParallel;

    @ConfigProperty(name = "aibridge.allowed-provider-hosts")
    Optional<List<String>> allowedProviderHosts;

    public boolean isUiRequired() {
        return uiRequired;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public int getQueueTimeoutMs() {
        return queueTimeoutMs;
    }

    public int getQueuePollIntervalMs() {
        return queuePollIntervalMs;
    }

    public int getQueueMaxDepth() {
        return queueMaxDepth;
    }

    public String getCacheMode() {
        return cacheMode;
    }

    public String getAuthApiKey() {
        return authApiKey;
    }

    public int getAuthTokenValidityMinutes() {
        return authTokenValidityMinutes;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public String getLogAppLevel() {
        return logAppLevel;
    }

    public int getLoadTestMaxParallel() {
        return loadTestMaxParallel;
    }

    public Optional<List<String>> getAllowedProviderHosts() {
        return allowedProviderHosts;
    }
}
