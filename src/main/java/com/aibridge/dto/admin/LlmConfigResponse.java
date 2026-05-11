package com.aibridge.dto.admin;

import com.aibridge.model.Feature;
import com.aibridge.model.LlmConfig;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LlmConfigResponse {

    private UUID id;

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("provider_id")
    private UUID providerId;

    @JsonProperty("model_name")
    private String modelName;

    @JsonProperty("endpoint_url")
    private String endpointUrl;

    private String credentials;

    @JsonProperty("rps_limit")
    private Integer rpsLimit;

    @JsonProperty("rpm_limit")
    private Integer rpmLimit;

    @JsonProperty("tpm_limit")
    private Integer tpmLimit;

    @JsonProperty("default_temperature")
    private Double defaultTemperature;

    @JsonProperty("default_max_tokens")
    private Integer defaultMaxTokens;

    @JsonProperty("default_top_p")
    private Double defaultTopP;

    @JsonProperty("default_n")
    private Integer defaultN;

    @JsonProperty("default_stop")
    private List<String> defaultStop;

    @JsonProperty("default_presence_penalty")
    private Double defaultPresencePenalty;

    @JsonProperty("default_frequency_penalty")
    private Double defaultFrequencyPenalty;

    @JsonProperty("queue_timeout_ms")
    private Integer queueTimeoutMs;

    @JsonProperty("is_fallback")
    private Boolean isFallback;

    private Integer priority;

    @JsonProperty("extra_params")
    private String extraParams;

    private List<String> features;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    @JsonProperty("provider_name")
    private String providerName;

    public LlmConfigResponse() {
    }

    public static LlmConfigResponse from(LlmConfig entity) {
        List<String> featureNames =
                entity.getFeatures() == null
                        ? List.of()
                        : entity.getFeatures().stream().map(Feature::getFeature).toList();

        LlmConfigResponse r = new LlmConfigResponse();
        r.setId(entity.getId());
        r.setTenantId(entity.getTenantId());
        r.setProviderId(entity.getProvider() != null ? entity.getProvider().getId() : null);
        r.setModelName(entity.getModelName());
        r.setEndpointUrl(entity.getEndpointUrl());
        r.setCredentials("****");
        r.setRpsLimit(entity.getRpsLimit());
        r.setRpmLimit(entity.getRpmLimit());
        r.setTpmLimit(entity.getTpmLimit());
        r.setDefaultTemperature(entity.getDefaultTemperature());
        r.setDefaultMaxTokens(entity.getDefaultMaxTokens());
        r.setDefaultTopP(entity.getDefaultTopP());
        r.setDefaultN(entity.getDefaultN());
        r.setDefaultStop(entity.getDefaultStop());
        r.setDefaultPresencePenalty(entity.getDefaultPresencePenalty());
        r.setDefaultFrequencyPenalty(entity.getDefaultFrequencyPenalty());
        r.setQueueTimeoutMs(entity.getQueueTimeoutMs());
        r.setIsFallback(entity.isFallback());
        r.setPriority(entity.getPriority());
        r.setExtraParams(entity.getExtraParams());
        r.setFeatures(featureNames);
        r.setIsActive(entity.isActive());
        r.setCreatedAt(entity.getCreatedAt());
        r.setUpdatedAt(entity.getUpdatedAt());
        r.setProviderName(
                entity.getProvider() != null && entity.getProvider().getName() != null
                        ? entity.getProvider().getName().name()
                        : null);
        return r;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getProviderId() {
        return providerId;
    }

    public void setProviderId(UUID providerId) {
        this.providerId = providerId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public String getCredentials() {
        return credentials;
    }

    public void setCredentials(String credentials) {
        this.credentials = credentials;
    }

    public Integer getRpsLimit() {
        return rpsLimit;
    }

    public void setRpsLimit(Integer rpsLimit) {
        this.rpsLimit = rpsLimit;
    }

    public Integer getRpmLimit() {
        return rpmLimit;
    }

    public void setRpmLimit(Integer rpmLimit) {
        this.rpmLimit = rpmLimit;
    }

    public Integer getTpmLimit() {
        return tpmLimit;
    }

    public void setTpmLimit(Integer tpmLimit) {
        this.tpmLimit = tpmLimit;
    }

    public Double getDefaultTemperature() {
        return defaultTemperature;
    }

    public void setDefaultTemperature(Double defaultTemperature) {
        this.defaultTemperature = defaultTemperature;
    }

    public Integer getDefaultMaxTokens() {
        return defaultMaxTokens;
    }

    public void setDefaultMaxTokens(Integer defaultMaxTokens) {
        this.defaultMaxTokens = defaultMaxTokens;
    }

    public Double getDefaultTopP() {
        return defaultTopP;
    }

    public void setDefaultTopP(Double defaultTopP) {
        this.defaultTopP = defaultTopP;
    }

    public Integer getDefaultN() {
        return defaultN;
    }

    public void setDefaultN(Integer defaultN) {
        this.defaultN = defaultN;
    }

    public List<String> getDefaultStop() {
        return defaultStop;
    }

    public void setDefaultStop(List<String> defaultStop) {
        this.defaultStop = defaultStop;
    }

    public Double getDefaultPresencePenalty() {
        return defaultPresencePenalty;
    }

    public void setDefaultPresencePenalty(Double defaultPresencePenalty) {
        this.defaultPresencePenalty = defaultPresencePenalty;
    }

    public Double getDefaultFrequencyPenalty() {
        return defaultFrequencyPenalty;
    }

    public void setDefaultFrequencyPenalty(Double defaultFrequencyPenalty) {
        this.defaultFrequencyPenalty = defaultFrequencyPenalty;
    }

    public Integer getQueueTimeoutMs() {
        return queueTimeoutMs;
    }

    public void setQueueTimeoutMs(Integer queueTimeoutMs) {
        this.queueTimeoutMs = queueTimeoutMs;
    }

    public Boolean getIsFallback() {
        return isFallback;
    }

    public void setIsFallback(Boolean isFallback) {
        this.isFallback = isFallback;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getExtraParams() {
        return extraParams;
    }

    public void setExtraParams(String extraParams) {
        this.extraParams = extraParams;
    }

    public List<String> getFeatures() {
        return features;
    }

    public void setFeatures(List<String> features) {
        this.features = features;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }
}
