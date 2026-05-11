package com.aibridge.dto.loadtest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoadTestResponse {

    @JsonProperty("run_id")
    private UUID runId;

    private String status;

    @JsonProperty("llm_config_id")
    private UUID llmConfigId;

    @JsonProperty("model_name")
    private String modelName;

    @JsonProperty("parallel_requests")
    private int parallelRequests;

    @JsonProperty("started_at")
    private Instant startedAt;

    @JsonProperty("completed_at")
    private Instant completedAt;

    private LoadTestSummary summary;

    private List<LoadTestResultItem> results;

    public LoadTestResponse() {
    }

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getLlmConfigId() {
        return llmConfigId;
    }

    public void setLlmConfigId(UUID llmConfigId) {
        this.llmConfigId = llmConfigId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public int getParallelRequests() {
        return parallelRequests;
    }

    public void setParallelRequests(int parallelRequests) {
        this.parallelRequests = parallelRequests;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public LoadTestSummary getSummary() {
        return summary;
    }

    public void setSummary(LoadTestSummary summary) {
        this.summary = summary;
    }

    public List<LoadTestResultItem> getResults() {
        return results;
    }

    public void setResults(List<LoadTestResultItem> results) {
        this.results = results;
    }
}
