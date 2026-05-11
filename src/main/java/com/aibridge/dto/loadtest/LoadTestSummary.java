package com.aibridge.dto.loadtest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoadTestSummary {

    private int total;

    private int success;

    private int failed;

    @JsonProperty("avg_latency_ms")
    private long avgLatencyMs;

    @JsonProperty("p50_latency_ms")
    private long p50LatencyMs;

    @JsonProperty("p95_latency_ms")
    private long p95LatencyMs;

    @JsonProperty("p99_latency_ms")
    private long p99LatencyMs;

    @JsonProperty("max_latency_ms")
    private long maxLatencyMs;

    @JsonProperty("min_latency_ms")
    private long minLatencyMs;

    public LoadTestSummary() {
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getSuccess() {
        return success;
    }

    public void setSuccess(int success) {
        this.success = success;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public long getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public void setAvgLatencyMs(long avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
    }

    public long getP50LatencyMs() {
        return p50LatencyMs;
    }

    public void setP50LatencyMs(long p50LatencyMs) {
        this.p50LatencyMs = p50LatencyMs;
    }

    public long getP95LatencyMs() {
        return p95LatencyMs;
    }

    public void setP95LatencyMs(long p95LatencyMs) {
        this.p95LatencyMs = p95LatencyMs;
    }

    public long getP99LatencyMs() {
        return p99LatencyMs;
    }

    public void setP99LatencyMs(long p99LatencyMs) {
        this.p99LatencyMs = p99LatencyMs;
    }

    public long getMaxLatencyMs() {
        return maxLatencyMs;
    }

    public void setMaxLatencyMs(long maxLatencyMs) {
        this.maxLatencyMs = maxLatencyMs;
    }

    public long getMinLatencyMs() {
        return minLatencyMs;
    }

    public void setMinLatencyMs(long minLatencyMs) {
        this.minLatencyMs = minLatencyMs;
    }
}
