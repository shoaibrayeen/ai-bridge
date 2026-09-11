package com.aibridge.dto.lineage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One attempt against one LLM config, with the tokens it consumed. Emitted for every link of the
 * failover chain the gateway touches, including the ones that failed, so a caller or an operator
 * can see which providers were tried and what each one cost.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallAttempt {

    /** 1-based position in the failover chain. */
    private int sequence;

    @JsonProperty("gateway_model_name")
    private String gatewayModelName;

    @JsonProperty("model_name")
    private String modelName;

    @JsonProperty("config_id")
    private String configId;

    private String provider;

    private AttemptOutcome outcome;

    @JsonProperty("duration_ms")
    private long durationMs;

    @JsonProperty("prompt_tokens")
    private Integer promptTokens;

    @JsonProperty("completion_tokens")
    private Integer completionTokens;

    @JsonProperty("total_tokens")
    private Integer totalTokens;

    /** Provider or gateway message explaining a non-terminal outcome. */
    private String detail;

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public String getGatewayModelName() {
        return gatewayModelName;
    }

    public void setGatewayModelName(String gatewayModelName) {
        this.gatewayModelName = gatewayModelName;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getConfigId() {
        return configId;
    }

    public void setConfigId(String configId) {
        this.configId = configId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public AttemptOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(AttemptOutcome outcome) {
        this.outcome = outcome;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    /** Compact one-line form for logs, e.g. {@code 2:ai-bridge-1-gpt-4o/OPENAI=SUCCESS(210ms,87t)}. */
    public String toLogString() {
        StringBuilder sb = new StringBuilder();
        sb.append(sequence).append(':').append(gatewayModelName).append('/').append(provider)
                .append('=').append(outcome).append('(').append(durationMs).append("ms");
        if (totalTokens != null) {
            sb.append(',').append(totalTokens).append('t');
        }
        sb.append(')');
        return sb.toString();
    }
}
