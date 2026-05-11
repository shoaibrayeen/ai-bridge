package com.aibridge.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public class LlmConfigRequest {

    @JsonProperty("tenant_id")
    private String tenantId;

    @NotNull
    @JsonProperty("provider_id")
    private UUID providerId;

    @NotBlank
    @JsonProperty("model_name")
    private String modelName;

    @NotBlank
    @JsonProperty("endpoint_url")
    private String endpointUrl;

    @NotBlank
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

    @NotEmpty
    private List<String> features;

    public LlmConfigRequest() {
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
}
