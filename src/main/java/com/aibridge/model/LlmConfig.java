package com.aibridge.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "llm_config")
public class LlmConfig extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private String tenantId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private LlmProvider provider;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    /** Normalised form of {@link #modelName}, used to partition {@link #modelSequence}. */
    @Column(name = "model_slug", nullable = false)
    private String modelSlug;

    /** Counts up per {@link #modelSlug}, so the same model can be registered more than once. */
    @Column(name = "model_sequence", nullable = false)
    private Integer modelSequence;

    /** Service-wide unique name, {@code ai-bridge-<sequence>-<slug>}. Clients may route on it. */
    @Column(name = "gateway_model_name", nullable = false, unique = true)
    private String gatewayModelName;

    @Column(name = "endpoint_url", nullable = false)
    private String endpointUrl;

    @Column(name = "credentials_encrypted")
    @JsonIgnore
    private String credentialsEncrypted;

    @Column(name = "rps_limit")
    private Integer rpsLimit;

    @Column(name = "rpm_limit")
    private Integer rpmLimit;

    @Column(name = "tpm_limit")
    private Integer tpmLimit;

    @Column(name = "default_temperature")
    private Double defaultTemperature;

    @Column(name = "default_max_tokens")
    private Integer defaultMaxTokens;

    @Column(name = "default_top_p")
    private Double defaultTopP;

    @Column(name = "default_n")
    private Integer defaultN;

    @Column(name = "default_stop", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> defaultStop;

    @Column(name = "default_presence_penalty")
    private Double defaultPresencePenalty;

    @Column(name = "default_frequency_penalty")
    private Double defaultFrequencyPenalty;

    @Column(name = "queue_timeout_ms", nullable = false)
    private Integer queueTimeoutMs = 5000;

    @Column(name = "is_fallback", nullable = false)
    private boolean isFallback;

    @Column(nullable = false)
    private int priority;

    @Column(name = "extra_params", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String extraParams;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @OneToMany(
            mappedBy = "llmConfig",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<Feature> features = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
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

    public LlmProvider getProvider() {
        return provider;
    }

    public void setProvider(LlmProvider provider) {
        this.provider = provider;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelSlug() {
        return modelSlug;
    }

    public void setModelSlug(String modelSlug) {
        this.modelSlug = modelSlug;
    }

    public Integer getModelSequence() {
        return modelSequence;
    }

    public void setModelSequence(Integer modelSequence) {
        this.modelSequence = modelSequence;
    }

    public String getGatewayModelName() {
        return gatewayModelName;
    }

    public void setGatewayModelName(String gatewayModelName) {
        this.gatewayModelName = gatewayModelName;
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

    public String getCredentialsEncrypted() {
        return credentialsEncrypted;
    }

    public void setCredentialsEncrypted(String credentialsEncrypted) {
        this.credentialsEncrypted = credentialsEncrypted;
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

    public boolean isFallback() {
        return isFallback;
    }

    public void setFallback(boolean fallback) {
        isFallback = fallback;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getExtraParams() {
        return extraParams;
    }

    public void setExtraParams(String extraParams) {
        this.extraParams = extraParams;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public List<Feature> getFeatures() {
        return features;
    }

    public void setFeatures(List<Feature> features) {
        this.features = features;
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
}
