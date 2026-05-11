package com.aibridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibridge.adapter.LlmProviderAdapter;
import com.aibridge.config.AiBridgeConfig;
import com.aibridge.dto.loadtest.LoadTestRequest;
import com.aibridge.dto.loadtest.LoadTestResponse;
import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatCompletionResponse;
import com.aibridge.dto.openai.Usage;
import com.aibridge.exception.ProviderRateLimitException;
import com.aibridge.exception.ProviderUnavailableException;
import com.aibridge.exception.QueueTimeoutException;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.LlmProvider;
import com.aibridge.model.enums.ProviderName;
import com.aibridge.repository.LlmConfigRepository;
import jakarta.enterprise.inject.Instance;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoadTestServiceTest {

    @Mock
    LlmProviderAdapter adapter;

    @Mock
    Instance<LlmProviderAdapter> adapterInstances;

    @Mock
    AiBridgeConfig aiBridgeConfig;

    @Mock
    LlmConfigRepository llmConfigRepository;

    @InjectMocks
    LoadTestService loadTestService;

    @BeforeEach
    void wire() throws Exception {
        when(adapter.getProviderName()).thenReturn(ProviderName.OPENAI);
        when(adapterInstances.iterator()).thenAnswer(inv -> List.of(adapter).iterator());
        when(aiBridgeConfig.getLoadTestMaxParallel()).thenReturn(20);

        Field inst = LoadTestService.class.getDeclaredField("adapterInstances");
        inst.setAccessible(true);
        inst.set(loadTestService, adapterInstances);

        Field cfg = LoadTestService.class.getDeclaredField("aiBridgeConfig");
        cfg.setAccessible(true);
        cfg.set(loadTestService, aiBridgeConfig);

        Field repo = LoadTestService.class.getDeclaredField("llmConfigRepository");
        repo.setAccessible(true);
        repo.set(loadTestService, llmConfigRepository);

        Method init = LoadTestService.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(loadTestService);
    }

    @AfterEach
    void stopRealCleanupScheduler() throws Exception {
        Field f = LoadTestService.class.getDeclaredField("cleanupScheduler");
        f.setAccessible(true);
        ScheduledExecutorService s = (ScheduledExecutorService) f.get(loadTestService);
        if (s != null && !mockingDetails(s).isMock()) {
            s.shutdownNow();
        }
        Thread.interrupted();
    }

    @Test
    void startLoadTest_createsRunAndReturnsUuid() {
        UUID cfgId = UUID.randomUUID();
        when(llmConfigRepository.findByIdOptional(cfgId)).thenReturn(Optional.of(sampleConfig(cfgId)));
        when(adapter.complete(any(), any())).thenReturn(new ChatCompletionResponse());

        LoadTestRequest req = new LoadTestRequest();
        req.setLlmConfigId(cfgId);
        req.setParallelRequests(2);
        req.setPrompt("ping");

        UUID runId = loadTestService.startLoadTest(req);

        assertNotNull(runId);
        LoadTestResponse tracked = loadTestService.getLoadTestResult(runId);
        assertNotNull(tracked);
        assertEquals(cfgId, tracked.getLlmConfigId());
    }

    @Test
    void getLoadTestResult_returnsStoredResultAfterCompletion() throws Exception {
        UUID cfgId = UUID.randomUUID();
        when(llmConfigRepository.findByIdOptional(cfgId)).thenReturn(Optional.of(sampleConfig(cfgId)));
        when(adapter.complete(any(), any())).thenReturn(new ChatCompletionResponse());

        LoadTestRequest req = new LoadTestRequest();
        req.setLlmConfigId(cfgId);
        req.setParallelRequests(1);
        req.setPrompt("go");

        UUID runId = loadTestService.startLoadTest(req);

        waitForStatus(runId, "COMPLETED");

        LoadTestResponse result = loadTestService.getLoadTestResult(runId);
        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());
        assertNotNull(result.getSummary());
        assertEquals(1, result.getSummary().getTotal());
    }

    @Test
    void startLoadTest_enforcesMaxParallelLimit() {
        when(aiBridgeConfig.getLoadTestMaxParallel()).thenReturn(5);

        LoadTestRequest req = new LoadTestRequest();
        req.setLlmConfigId(UUID.randomUUID());
        req.setParallelRequests(6);
        req.setPrompt("x");

        assertThrows(IllegalArgumentException.class, () -> loadTestService.startLoadTest(req));
    }

    @Test
    void startLoadTest_rejectsUnknownConfigId() {
        UUID missing = UUID.randomUUID();
        when(llmConfigRepository.findByIdOptional(missing)).thenReturn(Optional.empty());

        LoadTestRequest req = new LoadTestRequest();
        req.setLlmConfigId(missing);
        req.setParallelRequests(1);
        req.setPrompt("x");

        assertThrows(IllegalArgumentException.class, () -> loadTestService.startLoadTest(req));
    }

    @Test
    void shutdown_awaitTerminationTrue_doesNotCallShutdownNow() throws Exception {
        ScheduledExecutorService mockSched = org.mockito.Mockito.mock(ScheduledExecutorService.class);
        doNothing().when(mockSched).shutdown();
        when(mockSched.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(true);
        replaceCleanupScheduler(mockSched);

        Method shutdown = LoadTestService.class.getDeclaredMethod("shutdown");
        shutdown.setAccessible(true);
        shutdown.invoke(loadTestService);

        verify(mockSched).shutdown();
        verify(mockSched).awaitTermination(5, TimeUnit.SECONDS);
        verify(mockSched, never()).shutdownNow();
    }

    @Test
    void shutdown_awaitTerminationFalse_callsShutdownNow() throws Exception {
        ScheduledExecutorService mockSched = org.mockito.Mockito.mock(ScheduledExecutorService.class);
        doNothing().when(mockSched).shutdown();
        when(mockSched.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(false);
        replaceCleanupScheduler(mockSched);

        Method shutdown = LoadTestService.class.getDeclaredMethod("shutdown");
        shutdown.setAccessible(true);
        shutdown.invoke(loadTestService);

        verify(mockSched).shutdown();
        verify(mockSched).awaitTermination(5, TimeUnit.SECONDS);
        verify(mockSched).shutdownNow();
    }

    @Test
    void shutdown_awaitTerminationInterrupted_callsShutdownNowAndRestoresInterrupt() throws Exception {
        ScheduledExecutorService mockSched = org.mockito.Mockito.mock(ScheduledExecutorService.class);
        doNothing().when(mockSched).shutdown();
        when(mockSched.awaitTermination(5, TimeUnit.SECONDS)).thenThrow(new InterruptedException("boom"));
        replaceCleanupScheduler(mockSched);

        Method shutdown = LoadTestService.class.getDeclaredMethod("shutdown");
        shutdown.setAccessible(true);
        shutdown.invoke(loadTestService);

        verify(mockSched).shutdown();
        verify(mockSched).awaitTermination(5, TimeUnit.SECONDS);
        verify(mockSched).shutdownNow();
        assertTrue(Thread.interrupted());
    }

    @Test
    void evictOldRuns_removesOldCompletedRun() throws Exception {
        ConcurrentHashMap<UUID, LoadTestResponse> runs = getRunsMap();
        UUID id = UUID.randomUUID();
        LoadTestResponse old = minimalResponse(id);
        old.setCompletedAt(Instant.now().minus(Duration.ofHours(2)));
        runs.put(id, old);

        invokeEvictOldRuns();

        assertNull(runs.get(id));
    }

    @Test
    void evictOldRuns_keepsRecentRun() throws Exception {
        ConcurrentHashMap<UUID, LoadTestResponse> runs = getRunsMap();
        UUID id = UUID.randomUUID();
        LoadTestResponse recent = minimalResponse(id);
        recent.setCompletedAt(Instant.now().minus(Duration.ofMinutes(5)));
        runs.put(id, recent);

        invokeEvictOldRuns();

        assertNotNull(runs.get(id));
    }

    @Test
    void evictOldRuns_evictsWhenCompletedAtNullButStartedAtOld() throws Exception {
        ConcurrentHashMap<UUID, LoadTestResponse> runs = getRunsMap();
        UUID id = UUID.randomUUID();
        LoadTestResponse r = minimalResponse(id);
        r.setCompletedAt(null);
        r.setStartedAt(Instant.now().minus(Duration.ofHours(2)));
        runs.put(id, r);

        invokeEvictOldRuns();

        assertNull(runs.get(id));
    }

    @Test
    void evictOldRuns_keepsWhenBothTimestampsNull() throws Exception {
        ConcurrentHashMap<UUID, LoadTestResponse> runs = getRunsMap();
        UUID id = UUID.randomUUID();
        LoadTestResponse r = minimalResponse(id);
        r.setCompletedAt(null);
        r.setStartedAt(null);
        runs.put(id, r);

        invokeEvictOldRuns();

        assertNotNull(runs.get(id));
    }

    @Test
    void listRecentRuns_stripsResultsFromCopies() throws Exception {
        UUID cfgId = UUID.randomUUID();
        when(llmConfigRepository.findByIdOptional(cfgId)).thenReturn(Optional.of(sampleConfig(cfgId)));
        when(adapter.complete(any(), any())).thenReturn(new ChatCompletionResponse());

        LoadTestRequest req = new LoadTestRequest();
        req.setLlmConfigId(cfgId);
        req.setParallelRequests(1);
        req.setPrompt("x");

        UUID runId = loadTestService.startLoadTest(req);
        waitForStatus(runId, "COMPLETED");

        LoadTestResponse stored = loadTestService.getLoadTestResult(runId);
        assertNotNull(stored.getResults());

        List<LoadTestResponse> listed = loadTestService.listRecentRuns();
        LoadTestResponse copy =
                listed.stream().filter(x -> runId.equals(x.getRunId())).findFirst().orElse(null);
        assertNotNull(copy);
        assertNull(copy.getResults());
        assertEquals(stored.getStatus(), copy.getStatus());
    }

    @Test
    void executeLoadTest_mapsProviderRateLimitTo429() throws Exception {
        assertFailedHttpStatus(new ProviderRateLimitException("rl"), 429);
    }

    @Test
    void executeLoadTest_mapsProviderUnavailableTo503() throws Exception {
        assertFailedHttpStatus(new ProviderUnavailableException("down"), 503);
    }

    @Test
    void executeLoadTest_mapsQueueTimeoutTo408() throws Exception {
        assertFailedHttpStatus(new QueueTimeoutException("cfg"), 408);
    }

    @Test
    void executeLoadTest_mapsIllegalArgumentTo400() throws Exception {
        assertFailedHttpStatus(new IllegalArgumentException("bad"), 400);
    }

    @Test
    void executeLoadTest_mapsGenericExceptionTo500() throws Exception {
        assertFailedHttpStatus(new RuntimeException("oops"), 500);
    }

    @Test
    void executeLoadTest_noAdapter_marksRunFailed() throws Exception {
        UUID cfgId = UUID.randomUUID();
        when(llmConfigRepository.findByIdOptional(cfgId)).thenReturn(Optional.of(sampleConfig(cfgId, ProviderName.CEREBRAS)));

        LoadTestRequest req = new LoadTestRequest();
        req.setLlmConfigId(cfgId);
        req.setParallelRequests(1);
        req.setPrompt("x");

        UUID runId = loadTestService.startLoadTest(req);
        waitForStatus(runId, "FAILED");

        LoadTestResponse r = loadTestService.getLoadTestResult(runId);
        assertEquals("FAILED", r.getStatus());
        assertNotNull(r.getSummary());
        assertEquals(1, r.getSummary().getTotal());
        assertEquals(0, r.getSummary().getSuccess());
        assertEquals(1, r.getSummary().getFailed());
    }

    @Test
    void startLoadTest_propagatesMaxTokensToCompletionRequest() throws Exception {
        UUID cfgId = UUID.randomUUID();
        when(llmConfigRepository.findByIdOptional(cfgId)).thenReturn(Optional.of(sampleConfig(cfgId)));
        when(adapter.complete(any(), any())).thenReturn(new ChatCompletionResponse());

        LoadTestRequest req = new LoadTestRequest();
        req.setLlmConfigId(cfgId);
        req.setParallelRequests(1);
        req.setPrompt("tok");
        req.setMaxTokens(100);

        ArgumentCaptor<ChatCompletionRequest> cap = ArgumentCaptor.forClass(ChatCompletionRequest.class);
        UUID runId = loadTestService.startLoadTest(req);
        waitForStatus(runId, "COMPLETED");

        verify(adapter, times(1)).complete(cap.capture(), any());
        assertEquals(100, cap.getValue().getMaxTokens());
    }

    @Test
    void extractTotalTokens_countsWhenUsageHasTotal() throws Exception {
        UUID cfgId = UUID.randomUUID();
        when(llmConfigRepository.findByIdOptional(cfgId)).thenReturn(Optional.of(sampleConfig(cfgId)));
        ChatCompletionResponse resp = new ChatCompletionResponse();
        Usage u = new Usage();
        u.setTotalTokens(77);
        resp.setUsage(u);
        when(adapter.complete(any(), any())).thenReturn(resp);

        LoadTestRequest req = new LoadTestRequest();
        req.setLlmConfigId(cfgId);
        req.setParallelRequests(1);
        req.setPrompt("u");

        UUID runId = loadTestService.startLoadTest(req);
        waitForStatus(runId, "COMPLETED");

        assertEquals(77, loadTestService.getLoadTestResult(runId).getResults().get(0).getTokensUsed());
    }

    @Test
    void extractTotalTokens_returnsZeroWhenUsageNull() throws Exception {
        UUID cfgId = UUID.randomUUID();
        when(llmConfigRepository.findByIdOptional(cfgId)).thenReturn(Optional.of(sampleConfig(cfgId)));
        ChatCompletionResponse resp = new ChatCompletionResponse();
        resp.setUsage(null);
        when(adapter.complete(any(), any())).thenReturn(resp);

        LoadTestRequest req = new LoadTestRequest();
        req.setLlmConfigId(cfgId);
        req.setParallelRequests(1);
        req.setPrompt("u");

        UUID runId = loadTestService.startLoadTest(req);
        waitForStatus(runId, "COMPLETED");

        assertEquals(0, loadTestService.getLoadTestResult(runId).getResults().get(0).getTokensUsed());
    }

    @Test
    void extractTotalTokens_returnsZeroWhenTotalTokensNull() throws Exception {
        UUID cfgId = UUID.randomUUID();
        when(llmConfigRepository.findByIdOptional(cfgId)).thenReturn(Optional.of(sampleConfig(cfgId)));
        ChatCompletionResponse resp = new ChatCompletionResponse();
        Usage u = new Usage();
        u.setTotalTokens(null);
        resp.setUsage(u);
        when(adapter.complete(any(), any())).thenReturn(resp);

        LoadTestRequest req = new LoadTestRequest();
        req.setLlmConfigId(cfgId);
        req.setParallelRequests(1);
        req.setPrompt("u");

        UUID runId = loadTestService.startLoadTest(req);
        waitForStatus(runId, "COMPLETED");

        assertEquals(0, loadTestService.getLoadTestResult(runId).getResults().get(0).getTokensUsed());
    }

    @Test
    void extractTotalTokens_returnsZeroWhenResponseNull() throws Exception {
        UUID cfgId = UUID.randomUUID();
        when(llmConfigRepository.findByIdOptional(cfgId)).thenReturn(Optional.of(sampleConfig(cfgId)));
        when(adapter.complete(any(), any())).thenReturn(null);

        LoadTestRequest req = new LoadTestRequest();
        req.setLlmConfigId(cfgId);
        req.setParallelRequests(1);
        req.setPrompt("u");

        UUID runId = loadTestService.startLoadTest(req);
        waitForStatus(runId, "COMPLETED");

        assertEquals(0, loadTestService.getLoadTestResult(runId).getResults().get(0).getTokensUsed());
    }

    @Test
    void buildSummary_mixedSuccessAndFailure() throws Exception {
        UUID cfgId = UUID.randomUUID();
        when(llmConfigRepository.findByIdOptional(cfgId)).thenReturn(Optional.of(sampleConfig(cfgId)));
        AtomicInteger calls = new AtomicInteger();
        when(adapter.complete(any(), any()))
                .thenAnswer(inv -> {
                    if (calls.getAndIncrement() == 0) {
                        return new ChatCompletionResponse();
                    }
                    throw new ProviderRateLimitException("second fails");
                });

        LoadTestRequest req = new LoadTestRequest();
        req.setLlmConfigId(cfgId);
        req.setParallelRequests(2);
        req.setPrompt("mix");

        UUID runId = loadTestService.startLoadTest(req);
        waitForStatus(runId, "COMPLETED");

        LoadTestResponse r = loadTestService.getLoadTestResult(runId);
        assertEquals(1, r.getSummary().getSuccess());
        assertEquals(1, r.getSummary().getFailed());
        assertEquals(2, r.getSummary().getTotal());
        assertEquals(2, r.getResults().size());
        long successCount = r.getResults().stream().filter(x -> "SUCCESS".equals(x.getStatus())).count();
        long failCount = r.getResults().stream().filter(x -> "FAILED".equals(x.getStatus())).count();
        assertEquals(1, successCount);
        assertEquals(1, failCount);
        assertNotNull(r.getSummary().getAvgLatencyMs());
    }

    @Test
    void getLoadTestResult_unknownUuid_returnsNull() {
        assertNull(loadTestService.getLoadTestResult(UUID.randomUUID()));
    }

    /**
     * While {@code executeLoadTest} blocks in {@code join()}, remove the run from the map so the
     * async catch block's {@code runs.get(runId)} sees null and skips mutating a missing entry.
     */
    @Test
    void startLoadTest_asyncCatch_skipsWhenRunEvictedDuringJoin() throws Exception {
        UUID cfgId = UUID.randomUUID();
        when(llmConfigRepository.findByIdOptional(cfgId)).thenReturn(Optional.of(sampleConfig(cfgId)));
        CountDownLatch releaseError = new CountDownLatch(1);
        when(adapter.complete(any(), any()))
                .thenAnswer(inv -> {
                    try {
                        assertTrue(releaseError.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("interrupted");
                    }
                    throw new AssertionError("forced worker failure");
                });

        LoadTestRequest req = new LoadTestRequest();
        req.setLlmConfigId(cfgId);
        req.setParallelRequests(1);
        req.setPrompt("race");

        UUID runId = loadTestService.startLoadTest(req);
        waitForStatus(runId, "RUNNING");
        getRunsMap().remove(runId);
        releaseError.countDown();

        for (int i = 0; i < 200; i++) {
            if (loadTestService.getLoadTestResult(runId) == null) {
                return;
            }
            Thread.sleep(25);
        }
        fail("expected run to be absent after async failure with evicted map entry");
    }

    @Test
    void startLoadTest_zeroParallel_producesEmptySummaryLatencies() throws Exception {
        UUID cfgId = UUID.randomUUID();
        when(llmConfigRepository.findByIdOptional(cfgId)).thenReturn(Optional.of(sampleConfig(cfgId)));

        LoadTestRequest req = new LoadTestRequest();
        req.setLlmConfigId(cfgId);
        req.setParallelRequests(0);
        req.setPrompt("z");

        UUID runId = loadTestService.startLoadTest(req);
        waitForStatus(runId, "COMPLETED");

        LoadTestResponse r = loadTestService.getLoadTestResult(runId);
        assertEquals(0, r.getSummary().getTotal());
        assertEquals(0, r.getSummary().getAvgLatencyMs());
        assertEquals(0, r.getSummary().getP50LatencyMs());
    }

    @Test
    void executeLoadTest_missingTrackedRun_returnsEarly() throws Exception {
        UUID cfgId = UUID.randomUUID();
        LlmConfig config = sampleConfig(cfgId);
        LoadTestRequest req = new LoadTestRequest();
        req.setLlmConfigId(cfgId);
        req.setParallelRequests(1);
        req.setPrompt("ghost");

        Method execute =
                LoadTestService.class.getDeclaredMethod("executeLoadTest", UUID.class, LlmConfig.class, LoadTestRequest.class);
        execute.setAccessible(true);
        execute.invoke(loadTestService, UUID.randomUUID(), config, req);

        verify(adapter, never()).complete(any(), any());
    }

    private void assertFailedHttpStatus(Exception thrownByAdapter, int expectedHttp) throws Exception {
        UUID cfgId = UUID.randomUUID();
        when(llmConfigRepository.findByIdOptional(cfgId)).thenReturn(Optional.of(sampleConfig(cfgId)));
        when(adapter.complete(any(), any())).thenThrow(thrownByAdapter);

        LoadTestRequest req = new LoadTestRequest();
        req.setLlmConfigId(cfgId);
        req.setParallelRequests(1);
        req.setPrompt("err");

        UUID runId = loadTestService.startLoadTest(req);
        waitForStatus(runId, "COMPLETED");

        LoadTestResponse r = loadTestService.getLoadTestResult(runId);
        assertEquals(1, r.getResults().size());
        assertEquals("FAILED", r.getResults().get(0).getStatus());
        assertEquals(expectedHttp, r.getResults().get(0).getHttpStatus());
    }

    private void waitForStatus(UUID runId, String status) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            LoadTestResponse r = loadTestService.getLoadTestResult(runId);
            if (r != null && status.equals(r.getStatus())) {
                return;
            }
            Thread.sleep(25);
        }
        fail("timeout waiting for status " + status + " runId=" + runId);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<UUID, LoadTestResponse> getRunsMap() throws Exception {
        Field runsField = LoadTestService.class.getDeclaredField("runs");
        runsField.setAccessible(true);
        return (ConcurrentHashMap<UUID, LoadTestResponse>) runsField.get(loadTestService);
    }

    private void invokeEvictOldRuns() throws Exception {
        Method m = LoadTestService.class.getDeclaredMethod("evictOldRuns");
        m.setAccessible(true);
        m.invoke(loadTestService);
    }

    private void replaceCleanupScheduler(ScheduledExecutorService replacement) throws Exception {
        Field f = LoadTestService.class.getDeclaredField("cleanupScheduler");
        f.setAccessible(true);
        ScheduledExecutorService current = (ScheduledExecutorService) f.get(loadTestService);
        if (current != null && !mockingDetails(current).isMock()) {
            current.shutdownNow();
        }
        f.set(loadTestService, replacement);
    }

    private static LoadTestResponse minimalResponse(UUID runId) {
        LoadTestResponse r = new LoadTestResponse();
        r.setRunId(runId);
        r.setStatus("COMPLETED");
        return r;
    }

    private static LlmConfig sampleConfig(UUID id) {
        return sampleConfig(id, ProviderName.OPENAI);
    }

    private static LlmConfig sampleConfig(UUID id, ProviderName provider) {
        LlmProvider p = new LlmProvider();
        p.setName(provider);
        LlmConfig c = new LlmConfig();
        c.setId(id);
        c.setProvider(p);
        c.setModelName("m");
        return c;
    }
}
