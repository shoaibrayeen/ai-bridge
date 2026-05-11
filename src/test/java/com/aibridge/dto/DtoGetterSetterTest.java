package com.aibridge.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aibridge.dto.admin.LlmConfigRequest;
import com.aibridge.dto.admin.LlmProviderRequest;
import com.aibridge.dto.admin.ValidationResultResponse;
import com.aibridge.dto.loadtest.LoadTestRequest;
import com.aibridge.dto.loadtest.LoadTestResponse;
import com.aibridge.dto.loadtest.LoadTestResultItem;
import com.aibridge.dto.loadtest.LoadTestSummary;
import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatMessage;
import com.aibridge.dto.openai.Choice;
import com.aibridge.dto.openai.Usage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DtoGetterSetterTest {

    @Test
    void chatMessage_gettersAndSetters() {
        ChatMessage m = new ChatMessage();
        m.setRole("user");
        m.setContent("hello");
        m.setName("n1");
        assertEquals("user", m.getRole());
        assertEquals("hello", m.getContent());
        assertEquals("n1", m.getName());
    }

    @Test
    void choice_gettersAndSetters() {
        Choice c = new Choice();
        ChatMessage msg = new ChatMessage();
        c.setIndex(2);
        c.setMessage(msg);
        c.setFinishReason("length");
        assertEquals(2, c.getIndex());
        assertSame(msg, c.getMessage());
        assertEquals("length", c.getFinishReason());
    }

    @Test
    void usage_gettersAndSetters() {
        Usage u = new Usage();
        u.setPromptTokens(1);
        u.setCompletionTokens(2);
        u.setTotalTokens(3);
        assertEquals(1, u.getPromptTokens());
        assertEquals(2, u.getCompletionTokens());
        assertEquals(3, u.getTotalTokens());
    }

    @Test
    void chatCompletionRequest_gettersSettersAndDefaultStream() {
        ChatCompletionRequest req = new ChatCompletionRequest();
        assertFalse(req.isStream());

        List<ChatMessage> messages = List.of(new ChatMessage());
        List<String> stop = List.of(".");

        req.setMessages(messages);
        req.setModel("m");
        req.setTemperature(0.5);
        req.setMaxTokens(128);
        req.setTopP(0.9);
        req.setN(1);
        req.setStop(stop);
        req.setPresencePenalty(0.1);
        req.setFrequencyPenalty(0.2);
        req.setStream(true);

        assertSame(messages, req.getMessages());
        assertEquals("m", req.getModel());
        assertEquals(0.5, req.getTemperature());
        assertEquals(128, req.getMaxTokens());
        assertEquals(0.9, req.getTopP());
        assertEquals(1, req.getN());
        assertSame(stop, req.getStop());
        assertEquals(0.1, req.getPresencePenalty());
        assertEquals(0.2, req.getFrequencyPenalty());
        assertTrue(req.isStream());
    }

    @Test
    void llmConfigRequest_gettersAndSetters() {
        LlmConfigRequest r = new LlmConfigRequest();
        UUID pid = UUID.randomUUID();
        List<String> stop = List.of("halt");
        List<String> features = List.of("f1");

        r.setTenantId("ten");
        r.setProviderId(pid);
        r.setModelName("model");
        r.setEndpointUrl("https://e");
        r.setCredentials("creds");
        r.setRpsLimit(1);
        r.setRpmLimit(2);
        r.setTpmLimit(3);
        r.setDefaultTemperature(0.1);
        r.setDefaultMaxTokens(10);
        r.setDefaultTopP(0.2);
        r.setDefaultN(3);
        r.setDefaultStop(stop);
        r.setDefaultPresencePenalty(0.3);
        r.setDefaultFrequencyPenalty(0.4);
        r.setQueueTimeoutMs(5000);
        r.setIsFallback(true);
        r.setPriority(9);
        r.setExtraParams("x");
        r.setFeatures(features);

        assertEquals("ten", r.getTenantId());
        assertEquals(pid, r.getProviderId());
        assertEquals("model", r.getModelName());
        assertEquals("https://e", r.getEndpointUrl());
        assertEquals("creds", r.getCredentials());
        assertEquals(1, r.getRpsLimit());
        assertEquals(2, r.getRpmLimit());
        assertEquals(3, r.getTpmLimit());
        assertEquals(0.1, r.getDefaultTemperature());
        assertEquals(10, r.getDefaultMaxTokens());
        assertEquals(0.2, r.getDefaultTopP());
        assertEquals(3, r.getDefaultN());
        assertSame(stop, r.getDefaultStop());
        assertEquals(0.3, r.getDefaultPresencePenalty());
        assertEquals(0.4, r.getDefaultFrequencyPenalty());
        assertEquals(5000, r.getQueueTimeoutMs());
        assertTrue(r.getIsFallback());
        assertEquals(9, r.getPriority());
        assertEquals("x", r.getExtraParams());
        assertSame(features, r.getFeatures());
    }

    @Test
    void llmProviderRequest_gettersAndSetters() {
        LlmProviderRequest r = new LlmProviderRequest();
        r.setName("OPENAI");
        r.setAuthType("API_KEY");
        r.setAuthEndpoint("https://auth");
        assertEquals("OPENAI", r.getName());
        assertEquals("API_KEY", r.getAuthType());
        assertEquals("https://auth", r.getAuthEndpoint());
    }

    @Test
    void validationResultResponse_gettersAndSetters() {
        ValidationResultResponse r = new ValidationResultResponse();
        r.setValid(true);
        r.setMessage("ok");
        r.setLatencyMs(42L);
        assertTrue(r.isValid());
        assertEquals("ok", r.getMessage());
        assertEquals(42L, r.getLatencyMs());
    }

    @Test
    void loadTestRequest_gettersAndSetters() {
        LoadTestRequest r = new LoadTestRequest();
        UUID cfg = UUID.randomUUID();
        r.setLlmConfigId(cfg);
        r.setParallelRequests(10);
        r.setPrompt("p");
        r.setMaxTokens(256);
        assertEquals(cfg, r.getLlmConfigId());
        assertEquals(10, r.getParallelRequests());
        assertEquals("p", r.getPrompt());
        assertEquals(256, r.getMaxTokens());
    }

    @Test
    void loadTestResponse_gettersAndSetters() {
        LoadTestResponse r = new LoadTestResponse();
        UUID runId = UUID.randomUUID();
        UUID cfgId = UUID.randomUUID();
        Instant s = Instant.now();
        Instant e = s.plusSeconds(1);
        LoadTestSummary summary = new LoadTestSummary();
        List<LoadTestResultItem> results = List.of(new LoadTestResultItem());

        r.setRunId(runId);
        r.setStatus("done");
        r.setLlmConfigId(cfgId);
        r.setModelName("m");
        r.setParallelRequests(5);
        r.setStartedAt(s);
        r.setCompletedAt(e);
        r.setSummary(summary);
        r.setResults(results);

        assertEquals(runId, r.getRunId());
        assertEquals("done", r.getStatus());
        assertEquals(cfgId, r.getLlmConfigId());
        assertEquals("m", r.getModelName());
        assertEquals(5, r.getParallelRequests());
        assertEquals(s, r.getStartedAt());
        assertEquals(e, r.getCompletedAt());
        assertSame(summary, r.getSummary());
        assertSame(results, r.getResults());
    }

    @Test
    void loadTestResultItem_gettersAndSetters() {
        LoadTestResultItem i = new LoadTestResultItem();
        i.setRequestIndex(3);
        i.setStatus("ok");
        i.setLatencyMs(100L);
        i.setHttpStatus(200);
        i.setTokensUsed(50);
        i.setError(null);
        assertEquals(3, i.getRequestIndex());
        assertEquals("ok", i.getStatus());
        assertEquals(100L, i.getLatencyMs());
        assertEquals(200, i.getHttpStatus());
        assertEquals(50, i.getTokensUsed());
        assertNull(i.getError());

        i.setError("boom");
        assertEquals("boom", i.getError());
    }

    @Test
    void loadTestSummary_gettersAndSetters() {
        LoadTestSummary s = new LoadTestSummary();
        s.setTotal(100);
        s.setSuccess(95);
        s.setFailed(5);
        s.setAvgLatencyMs(10L);
        s.setP50LatencyMs(8L);
        s.setP95LatencyMs(20L);
        s.setP99LatencyMs(30L);
        s.setMaxLatencyMs(40L);
        s.setMinLatencyMs(5L);
        assertEquals(100, s.getTotal());
        assertEquals(95, s.getSuccess());
        assertEquals(5, s.getFailed());
        assertEquals(10L, s.getAvgLatencyMs());
        assertEquals(8L, s.getP50LatencyMs());
        assertEquals(20L, s.getP95LatencyMs());
        assertEquals(30L, s.getP99LatencyMs());
        assertEquals(40L, s.getMaxLatencyMs());
        assertEquals(5L, s.getMinLatencyMs());
    }
}
