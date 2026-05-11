package com.aibridge.service;

import com.aibridge.adapter.LlmProviderAdapter;
import com.aibridge.dto.admin.ValidationResultResponse;
import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatMessage;
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

@ApplicationScoped
public class LlmValidationService {

    @Inject
    @Any
    Instance<LlmProviderAdapter> adapterInstances;

    @Inject
    EncryptionService encryptionService;

    private Map<ProviderName, LlmProviderAdapter> adapterByProvider;

    @PostConstruct
    void initAdapters() {
        adapterByProvider = new EnumMap<>(ProviderName.class);
        for (LlmProviderAdapter adapter : adapterInstances) {
            adapterByProvider.put(adapter.getProviderName(), adapter);
        }
    }

    public ValidationResultResponse validate(LlmConfig config) {
        long startNs = System.nanoTime();
        long latencyMs = 0L;
        try {
            if (config.getCredentialsEncrypted() != null && !config.getCredentialsEncrypted().isBlank()) {
                encryptionService.decrypt(config.getCredentialsEncrypted());
            }

            ProviderName providerName = config.getProvider().getName();
            LlmProviderAdapter adapter = adapterByProvider.get(providerName);
            if (adapter == null) {
                latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
                ValidationResultResponse err = new ValidationResultResponse();
                err.setValid(false);
                err.setMessage("No adapter for provider: " + providerName);
                err.setLatencyMs(latencyMs);
                return err;
            }

            ChatMessage user = new ChatMessage();
            user.setRole("user");
            user.setContent("Say hello");

            ChatCompletionRequest testRequest = new ChatCompletionRequest();
            testRequest.setMessages(List.of(user));
            testRequest.setMaxTokens(10);

            adapter.complete(testRequest, config);

            latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            ValidationResultResponse ok = new ValidationResultResponse();
            ok.setValid(true);
            ok.setMessage("Connection successful");
            ok.setLatencyMs(latencyMs);
            return ok;
        } catch (Exception e) {
            latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            ValidationResultResponse fail = new ValidationResultResponse();
            fail.setValid(false);
            fail.setMessage(e.getMessage());
            fail.setLatencyMs(latencyMs);
            return fail;
        }
    }
}
