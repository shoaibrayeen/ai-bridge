package com.aibridge.dto.admin;

import com.aibridge.model.LlmProvider;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LlmProviderResponse {

    private UUID id;

    private String name;

    @JsonProperty("auth_type")
    private String authType;

    @JsonProperty("auth_endpoint")
    private String authEndpoint;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    public LlmProviderResponse() {
    }

    public static LlmProviderResponse from(LlmProvider entity) {
        LlmProviderResponse r = new LlmProviderResponse();
        r.setId(entity.getId());
        r.setName(entity.getName() != null ? entity.getName().name() : null);
        r.setAuthType(entity.getAuthType() != null ? entity.getAuthType().name() : null);
        r.setAuthEndpoint(entity.getAuthEndpoint());
        r.setCreatedAt(entity.getCreatedAt());
        r.setUpdatedAt(entity.getUpdatedAt());
        return r;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getAuthEndpoint() {
        return authEndpoint;
    }

    public void setAuthEndpoint(String authEndpoint) {
        this.authEndpoint = authEndpoint;
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
