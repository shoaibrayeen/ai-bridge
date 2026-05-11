package com.aibridge.service;

import com.aibridge.adapter.LlmProviderAdapter;
import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatCompletionResponse;
import com.aibridge.dto.openai.Choice;
import com.aibridge.dto.openai.Usage;
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
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Resolving failover chain for tenantId=" + tenantId + ", feature=" + feature);
        }
        List<LlmConfig> chain = configResolverService.resolveChain(tenantId, feature);

        for (LlmConfig config : chain) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Trying LLM config id=" + config.getId() + ", model=" + config.getModelName());
            }

            ChatCompletionRequest mergedRequest = parameterMergeService.merge(request, config);

            try {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Acquiring pacing slot for config id=" + config.getId());
                }
                requestPacingService.acquireSlot(config);
            } catch (QueueTimeoutException e) {
                LOG.warning("Queue timeout for config id=" + config.getId() + ": " + e.getMessage());
                continue;
            }

            ProviderName providerName = config.getProvider().getName();
            LlmProviderAdapter adapter = adapterByProvider.get(providerName);
            if (adapter == null) {
                LOG.warning("No adapter registered for provider " + providerName + ", skipping config id=" + config.getId());
                continue;
            }

            try {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Calling provider adapter for config id=" + config.getId());
                }
                ChatCompletionResponse response = adapter.complete(mergedRequest, config);

                if (isEmptyOrNullContent(response)) {
                    LOG.warning("Empty or null assistant content from config id=" + config.getId() + ", returning empty completion");
                    return ChatCompletionResponse.empty(config.getModelName());
                }

                Usage usage = response.getUsage();
                if (usage != null && usage.getTotalTokens() != null) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("Reconciling TPM tokens=" + usage.getTotalTokens() + " for config id=" + config.getId());
                    }
                    requestPacingService.reconcileTokens(config, usage.getTotalTokens());
                }

                return response;
            } catch (ProviderRateLimitException e) {
                LOG.warning("Provider rate limit for config id=" + config.getId() + ": " + e.getMessage());
            } catch (ProviderUnavailableException e) {
                LOG.warning("Provider unavailable for config id=" + config.getId() + ": " + e.getMessage());
            }
        }

        throw new ProviderUnavailableException("All LLM providers in failover chain exhausted");
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
