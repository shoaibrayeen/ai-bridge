package com.aibridge.dto.loadtest;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class LoadTestRequest {

    @NotNull
    @JsonProperty("llm_config_id")
    private UUID llmConfigId;

    @Min(1)
    @Max(500)
    @JsonProperty("parallel_requests")
    private int parallelRequests;

    @NotBlank
    private String prompt;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    public LoadTestRequest() {
    }

    public UUID getLlmConfigId() {
        return llmConfigId;
    }

    public void setLlmConfigId(UUID llmConfigId) {
        this.llmConfigId = llmConfigId;
    }

    public int getParallelRequests() {
        return parallelRequests;
    }

    public void setParallelRequests(int parallelRequests) {
        this.parallelRequests = parallelRequests;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }
}
