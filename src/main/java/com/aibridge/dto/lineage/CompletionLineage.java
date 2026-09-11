package com.aibridge.dto.lineage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The full path one chat completion took through the gateway: how it was routed, every config it
 * touched, and the tokens each attempt consumed.
 *
 * <p>Always written to the log alongside the request id. Returned to the caller only when the
 * request carries {@code X-Include-Lineage: true}, so the default response stays a clean OpenAI
 * payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompletionLineage {

    /** How the chain was chosen: {@code model} when pinned by name, {@code feature} otherwise. */
    public static final String ROUTING_MODEL = "model";

    public static final String ROUTING_FEATURE = "feature";

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("tenant_id")
    private String tenantId;

    private String feature;

    /** {@code model} or {@code feature}. */
    private String routing;

    @JsonProperty("chain_length")
    private int chainLength;

    private List<CallAttempt> attempts = new ArrayList<>();

    @JsonProperty("total_duration_ms")
    private long totalDurationMs;

    /** Tokens billed across every attempt, not just the one that succeeded. */
    @JsonProperty("billed_total_tokens")
    private Integer billedTotalTokens;

    public void add(CallAttempt attempt) {
        attempts.add(attempt);
        totalDurationMs += attempt.getDurationMs();
    }

    /** Compact one-line form for logs. */
    public String toLogString() {
        return "lineage requestId=" + requestId
                + " tenant=" + tenantId
                + " feature=" + feature
                + " routing=" + routing
                + " chain=" + chainLength
                + " totalMs=" + totalDurationMs
                + " billedTokens=" + getBilledTotalTokens()
                + " path=[" + attempts.stream().map(CallAttempt::toLogString).collect(Collectors.joining(" -> ")) + "]";
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public String getRouting() {
        return routing;
    }

    public void setRouting(String routing) {
        this.routing = routing;
    }

    public int getChainLength() {
        return chainLength;
    }

    public void setChainLength(int chainLength) {
        this.chainLength = chainLength;
    }

    public List<CallAttempt> getAttempts() {
        return attempts;
    }

    public void setAttempts(List<CallAttempt> attempts) {
        this.attempts = attempts;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    /**
     * Derived from the attempts rather than accumulated, because a streaming attempt only learns
     * its token count once the client has drained the stream — long after the attempt was added.
     */
    public Integer getBilledTotalTokens() {
        if (billedTotalTokens != null) {
            return billedTotalTokens;
        }
        Integer sum = null;
        for (CallAttempt attempt : attempts) {
            if (attempt.getTotalTokens() != null) {
                sum = (sum == null ? 0 : sum) + attempt.getTotalTokens();
            }
        }
        return sum;
    }

    public void setBilledTotalTokens(Integer billedTotalTokens) {
        this.billedTotalTokens = billedTotalTokens;
    }
}
