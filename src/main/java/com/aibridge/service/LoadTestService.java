package com.aibridge.service;

import com.aibridge.adapter.LlmProviderAdapter;
import com.aibridge.config.AiBridgeConfig;
import com.aibridge.dto.loadtest.LoadTestRequest;
import com.aibridge.dto.loadtest.LoadTestResponse;
import com.aibridge.dto.loadtest.LoadTestResultItem;
import com.aibridge.dto.loadtest.LoadTestSummary;
import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatCompletionResponse;
import com.aibridge.dto.openai.ChatMessage;
import com.aibridge.exception.ProviderRateLimitException;
import com.aibridge.exception.ProviderUnavailableException;
import com.aibridge.exception.QueueTimeoutException;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.enums.ProviderName;
import com.aibridge.repository.LlmConfigRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class LoadTestService {

    private static final Logger LOG = Logger.getLogger(LoadTestService.class.getName());

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_FAILED = "FAILED";

    @Inject
    @Any
    Instance<LlmProviderAdapter> adapterInstances;

    @Inject
    AiBridgeConfig aiBridgeConfig;

    @Inject
    LlmConfigRepository llmConfigRepository;

    private Map<ProviderName, LlmProviderAdapter> adapterByProvider;

    private final ConcurrentHashMap<UUID, LoadTestResponse> runs = new ConcurrentHashMap<>();

    private ScheduledExecutorService cleanupScheduler;

    @PostConstruct
    void init() {
        adapterByProvider = new EnumMap<>(ProviderName.class);
        for (LlmProviderAdapter adapter : adapterInstances) {
            adapterByProvider.put(adapter.getProviderName(), adapter);
        }

        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "load-test-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupScheduler.scheduleAtFixedRate(this::evictOldRuns, 10, 10, TimeUnit.MINUTES);
    }

    @PreDestroy
    void shutdown() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public UUID startLoadTest(LoadTestRequest request) {
        int maxParallel = aiBridgeConfig.getLoadTestMaxParallel();
        if (request.getParallelRequests() > maxParallel) {
            throw new IllegalArgumentException(
                    "parallelRequests must be <= " + maxParallel + " (aibridge.load-test.max-parallel)");
        }

        LlmConfig config =
                llmConfigRepository.findByIdOptional(request.getLlmConfigId()).orElseThrow(() -> new IllegalArgumentException(
                        "LlmConfig not found: " + request.getLlmConfigId()));

        UUID runId = UUID.randomUUID();
        LoadTestResponse response = new LoadTestResponse();
        response.setRunId(runId);
        response.setStatus(STATUS_PENDING);
        response.setLlmConfigId(request.getLlmConfigId());
        response.setModelName(config.getModelName());
        response.setParallelRequests(request.getParallelRequests());
        response.setStartedAt(Instant.now());

        runs.put(runId, response);

        CompletableFuture.runAsync(() -> {
            try {
                executeLoadTest(runId, config, request);
            } catch (Exception e) {
                LOG.warning("Load test run " + runId + " failed: " + e.getMessage());
                LoadTestResponse r = runs.get(runId);
                if (r != null) {
                    synchronized (r) {
                        r.setStatus(STATUS_FAILED);
                        r.setCompletedAt(Instant.now());
                        LoadTestSummary summary = new LoadTestSummary();
                        summary.setTotal(request.getParallelRequests());
                        summary.setSuccess(0);
                        summary.setFailed(request.getParallelRequests());
                        r.setSummary(summary);
                    }
                }
            }
        });

        return runId;
    }

    private void executeLoadTest(UUID runId, LlmConfig config, LoadTestRequest request) {
        LoadTestResponse tracked = runs.get(runId);
        if (tracked == null) {
            return;
        }

        synchronized (tracked) {
            tracked.setStatus(STATUS_RUNNING);
        }

        ChatCompletionRequest completionRequest = new ChatCompletionRequest();
        ChatMessage user = new ChatMessage();
        user.setRole("user");
        user.setContent(request.getPrompt());
        completionRequest.setMessages(List.of(user));
        completionRequest.setModel(config.getModelName());
        if (request.getMaxTokens() != null) {
            completionRequest.setMaxTokens(request.getMaxTokens());
        }

        ProviderName providerName = config.getProvider().getName();
        LlmProviderAdapter adapter = adapterByProvider.get(providerName);
        if (adapter == null) {
            synchronized (tracked) {
                tracked.setStatus(STATUS_FAILED);
                tracked.setCompletedAt(Instant.now());
            }
            throw new IllegalStateException("No adapter for provider: " + providerName);
        }

        int n = request.getParallelRequests();
        List<CompletableFuture<LoadTestResultItem>> futures = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final int index = i;
            futures.add(CompletableFuture.supplyAsync(() -> runSingleRequest(index, adapter, completionRequest, config)));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).join();

        List<LoadTestResultItem> results = new ArrayList<>(n);
        for (CompletableFuture<LoadTestResultItem> f : futures) {
            results.add(f.join());
        }

        results.sort(Comparator.comparingInt(LoadTestResultItem::getRequestIndex));

        LoadTestSummary summary = buildSummary(results);

        synchronized (tracked) {
            tracked.setStatus(STATUS_COMPLETED);
            tracked.setCompletedAt(Instant.now());
            tracked.setSummary(summary);
            tracked.setResults(results);
        }
    }

    private LoadTestResultItem runSingleRequest(
            int index, LlmProviderAdapter adapter, ChatCompletionRequest completionRequest, LlmConfig config) {
        long startNs = System.nanoTime();
        try {
            ChatCompletionResponse response = adapter.complete(completionRequest, config);
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;

            LoadTestResultItem item = new LoadTestResultItem();
            item.setRequestIndex(index);
            item.setStatus(RESULT_SUCCESS);
            item.setLatencyMs(latencyMs);
            item.setHttpStatus(200);
            item.setTokensUsed(extractTotalTokens(response));
            item.setError(null);
            return item;
        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            LoadTestResultItem item = new LoadTestResultItem();
            item.setRequestIndex(index);
            item.setStatus(RESULT_FAILED);
            item.setLatencyMs(latencyMs);
            item.setHttpStatus(mapHttpStatus(e));
            item.setTokensUsed(0);
            item.setError(e.getMessage());
            return item;
        }
    }

    private static int extractTotalTokens(ChatCompletionResponse response) {
        if (response == null || response.getUsage() == null || response.getUsage().getTotalTokens() == null) {
            return 0;
        }
        return response.getUsage().getTotalTokens();
    }

    private static int mapHttpStatus(Throwable e) {
        if (e instanceof ProviderRateLimitException) {
            return 429;
        }
        if (e instanceof ProviderUnavailableException) {
            return 503;
        }
        if (e instanceof QueueTimeoutException) {
            return 408;
        }
        if (e instanceof IllegalArgumentException) {
            return 400;
        }
        return 500;
    }

    private static LoadTestSummary buildSummary(List<LoadTestResultItem> results) {
        LoadTestSummary s = new LoadTestSummary();
        int total = results.size();
        long successCount = results.stream().filter(r -> RESULT_SUCCESS.equals(r.getStatus())).count();
        s.setTotal(total);
        s.setSuccess((int) successCount);
        s.setFailed(total - (int) successCount);

        List<Long> latencies =
                results.stream().map(LoadTestResultItem::getLatencyMs).sorted().collect(Collectors.toList());

        if (latencies.isEmpty()) {
            s.setAvgLatencyMs(0);
            s.setP50LatencyMs(0);
            s.setP95LatencyMs(0);
            s.setP99LatencyMs(0);
            s.setMaxLatencyMs(0);
            s.setMinLatencyMs(0);
            return s;
        }

        long sum = 0;
        for (long l : latencies) {
            sum += l;
        }
        s.setAvgLatencyMs(sum / latencies.size());
        s.setMinLatencyMs(latencies.get(0));
        s.setMaxLatencyMs(latencies.get(latencies.size() - 1));
        s.setP50LatencyMs(percentile(latencies, 50));
        s.setP95LatencyMs(percentile(latencies, 95));
        s.setP99LatencyMs(percentile(latencies, 99));
        return s;
    }

    /**
     * Nearest-rank percentile on sorted ascending latencies.
     */
    private static long percentile(List<Long> sorted, double p) {
        int n = sorted.size();
        int k = (int) Math.ceil(p / 100.0 * n);
        k = Math.max(1, Math.min(n, k));
        return sorted.get(k - 1);
    }

    private void evictOldRuns() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        for (Map.Entry<UUID, LoadTestResponse> e : runs.entrySet()) {
            LoadTestResponse r = e.getValue();
            Instant ref = r.getCompletedAt() != null ? r.getCompletedAt() : r.getStartedAt();
            if (ref != null && ref.isBefore(cutoff)) {
                runs.remove(e.getKey(), r);
            }
        }
    }

    public LoadTestResponse getLoadTestResult(UUID runId) {
        return runs.get(runId);
    }

    public List<LoadTestResponse> listRecentRuns() {
        return runs.values().stream().map(LoadTestService::copyWithoutResults).collect(Collectors.toList());
    }

    private static LoadTestResponse copyWithoutResults(LoadTestResponse src) {
        LoadTestResponse c = new LoadTestResponse();
        c.setRunId(src.getRunId());
        c.setStatus(src.getStatus());
        c.setLlmConfigId(src.getLlmConfigId());
        c.setModelName(src.getModelName());
        c.setParallelRequests(src.getParallelRequests());
        c.setStartedAt(src.getStartedAt());
        c.setCompletedAt(src.getCompletedAt());
        c.setSummary(src.getSummary());
        c.setResults(null);
        return c;
    }
}
