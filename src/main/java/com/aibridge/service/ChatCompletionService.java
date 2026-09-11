package com.aibridge.service;

import com.aibridge.adapter.LlmProviderAdapter;
import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatCompletionResponse;
import com.aibridge.dto.openai.Choice;
import com.aibridge.dto.openai.Usage;
import com.aibridge.dto.lineage.AttemptOutcome;
import com.aibridge.dto.lineage.CallAttempt;
import com.aibridge.dto.lineage.CompletionLineage;
import com.aibridge.filter.RequestCorrelationFilter;
import com.aibridge.exception.ProviderRateLimitException;
import com.aibridge.exception.ProviderUnavailableException;
import com.aibridge.exception.QueueTimeoutException;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.enums.ProviderName;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class ChatCompletionService {

    private static final Logger LOG = Logger.getLogger(ChatCompletionService.class.getName());

    @Inject
    ConfigResolverService configResolverService;

    @Inject
    ParameterMergeService parameterMergeService;

    @Inject
    RequestPacingService requestPacingService;

    @Inject
    @Any
    Instance<LlmProviderAdapter> adapterInstances;

    private Map<ProviderName, LlmProviderAdapter> adapterByProvider;

    @PostConstruct
    void initAdapters() {
        adapterByProvider = new EnumMap<>(ProviderName.class);
        for (LlmProviderAdapter adapter : adapterInstances) {
            adapterByProvider.put(adapter.getProviderName(), adapter);
        }
    }

    public ChatCompletionResponse complete(String tenantId, String feature, ChatCompletionRequest request) {
        boolean modelPinned =
                request != null && GatewayModelNameService.isGatewayModelName(request.getModel());
        List<LlmConfig> chain = resolveChain(tenantId, feature, request, modelPinned);

        CompletionLineage lineage = new CompletionLineage();
        lineage.setRequestId(RequestCorrelationFilter.currentRequestId());
        lineage.setTenantId(tenantId);
        lineage.setFeature(feature);
        lineage.setRouting(modelPinned ? CompletionLineage.ROUTING_MODEL : CompletionLineage.ROUTING_FEATURE);
        lineage.setChainLength(chain.size());

        int position = 0;
        for (LlmConfig config : chain) {
            position++;
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Trying LLM config id=" + config.getId() + ", model=" + config.getModelName());
            }

            CallAttempt attempt = newAttempt(position, config);
            long startedAt = System.nanoTime();

            ChatCompletionRequest mergedRequest = parameterMergeService.merge(request, config);

            try {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Acquiring pacing slot for config id=" + config.getId());
                }
                requestPacingService.acquireSlot(config);
            } catch (QueueTimeoutException e) {
                LOG.warning("Queue timeout for config id=" + config.getId() + ": " + e.getMessage());
                finish(lineage, attempt, startedAt, AttemptOutcome.QUEUE_TIMEOUT, e.getMessage());
                continue;
            }

            ProviderName providerName = config.getProvider().getName();
            LlmProviderAdapter adapter = adapterByProvider.get(providerName);
            if (adapter == null) {
                LOG.warning("No adapter registered for provider " + providerName + ", skipping config id=" + config.getId());
                finish(lineage, attempt, startedAt, AttemptOutcome.NO_ADAPTER,
                        "No adapter registered for provider " + providerName);
                continue;
            }

            try {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Calling provider adapter for config id=" + config.getId());
                }
                ChatCompletionResponse response = adapter.complete(mergedRequest, config);

                if (isEmptyOrNullContent(response)) {
                    LOG.warning("Empty or null assistant content from config id=" + config.getId() + ", returning empty completion");
                    recordTokens(attempt, response);
                    finish(lineage, attempt, startedAt, AttemptOutcome.EMPTY_RESPONSE, "No assistant content");
                    logLineage(lineage);
                    ChatCompletionResponse empty = ChatCompletionResponse.empty(config.getGatewayModelName());
                    empty.setLineage(lineage);
                    return empty;
                }

                recordTokens(attempt, response);

                Usage usage = response.getUsage();
                if (usage != null && usage.getTotalTokens() != null) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("Reconciling TPM tokens=" + usage.getTotalTokens() + " for config id=" + config.getId());
                    }
                    requestPacingService.reconcileTokens(config, usage.getTotalTokens());
                }

                finish(lineage, attempt, startedAt, AttemptOutcome.SUCCESS, null);
                logLineage(lineage);

                // Echo the gateway identity, not the provider's, so a caller can send the same
                // value back and reach the same config.
                response.setModel(config.getGatewayModelName());
                response.setLineage(lineage);
                return response;
            } catch (ProviderRateLimitException e) {
                LOG.warning("Provider rate limit for config id=" + config.getId() + ": " + e.getMessage());
                finish(lineage, attempt, startedAt, AttemptOutcome.RATE_LIMITED, e.getMessage());
            } catch (ProviderUnavailableException e) {
                LOG.warning("Provider unavailable for config id=" + config.getId() + ": " + e.getMessage());
                finish(lineage, attempt, startedAt, AttemptOutcome.UNAVAILABLE, e.getMessage());
            }
        }

        logLineage(lineage);
        throw new ProviderUnavailableException("All LLM providers in failover chain exhausted");
    }

    private static CallAttempt newAttempt(int position, LlmConfig config) {
        CallAttempt attempt = new CallAttempt();
        attempt.setSequence(position);
        attempt.setConfigId(config.getId() == null ? null : config.getId().toString());
        attempt.setGatewayModelName(config.getGatewayModelName());
        attempt.setModelName(config.getModelName());
        attempt.setProvider(config.getProvider() == null ? null : config.getProvider().getName().name());
        return attempt;
    }

    private static void recordTokens(CallAttempt attempt, ChatCompletionResponse response) {
        Usage usage = response == null ? null : response.getUsage();
        if (usage == null) {
            return;
        }
        attempt.setPromptTokens(usage.getPromptTokens());
        attempt.setCompletionTokens(usage.getCompletionTokens());
        attempt.setTotalTokens(usage.getTotalTokens());
    }

    private static void finish(
            CompletionLineage lineage,
            CallAttempt attempt,
            long startedAtNanos,
            AttemptOutcome outcome,
            String detail) {
        attempt.setDurationMs((System.nanoTime() - startedAtNanos) / 1_000_000L);
        attempt.setOutcome(outcome);
        attempt.setDetail(detail);
        lineage.add(attempt);
    }

    private static void logLineage(CompletionLineage lineage) {
        LOG.info(lineage.toLogString());
    }

    /**
     * A {@code model} field carrying a gateway model name pins the request to that one config.
     * Anything else (including a provider's own model id, which OpenAI clients are obliged to send)
     * falls through to feature-based routing, which is what builds a failover chain.
     */
    private List<LlmConfig> resolveChain(
            String tenantId, String feature, ChatCompletionRequest request, boolean modelPinned) {
        if (modelPinned) {
            String model = request.getModel();
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Routing by gateway model name=" + model + " for tenantId=" + tenantId);
            }
            return configResolverService.resolveByGatewayModelName(tenantId, model);
        }
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Resolving failover chain for tenantId=" + tenantId + ", feature=" + feature);
        }
        return configResolverService.resolveChain(tenantId, feature);
    }

    private static boolean isEmptyOrNullContent(ChatCompletionResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return true;
        }
        Choice first = response.getChoices().get(0);
        if (first == null || first.getMessage() == null) {
            return true;
        }
        String content = first.getMessage().getContent();
        return content == null || content.isBlank();
    }
}
