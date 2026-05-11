package com.aibridge.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class LlmProviderRequest {

    @NotBlank
    private String name;

    @NotBlank
    @JsonProperty("auth_type")
    private String authType;

    @JsonProperty("auth_endpoint")
    private String authEndpoint;

    public LlmProviderRequest() {
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
}
