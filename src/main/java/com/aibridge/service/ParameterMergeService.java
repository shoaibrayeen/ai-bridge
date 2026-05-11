package com.aibridge.service;

import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.model.LlmConfig;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ParameterMergeService {

    public ChatCompletionRequest merge(ChatCompletionRequest request, LlmConfig config) {
        ChatCompletionRequest merged = new ChatCompletionRequest();
        merged.setMessages(request.getMessages());
        merged.setStream(request.isStream());

        merged.setModel(config.getModelName());

        merged.setTemperature(
                request.getTemperature() != null ? request.getTemperature() : config.getDefaultTemperature());
        merged.setMaxTokens(
                request.getMaxTokens() != null ? request.getMaxTokens() : config.getDefaultMaxTokens());
        merged.setTopP(request.getTopP() != null ? request.getTopP() : config.getDefaultTopP());
        merged.setN(request.getN() != null ? request.getN() : config.getDefaultN());
        merged.setStop(request.getStop() != null ? request.getStop() : config.getDefaultStop());
        merged.setPresencePenalty(
                request.getPresencePenalty() != null
                        ? request.getPresencePenalty()
                        : config.getDefaultPresencePenalty());
        merged.setFrequencyPenalty(
                request.getFrequencyPenalty() != null
                        ? request.getFrequencyPenalty()
                        : config.getDefaultFrequencyPenalty());

        return merged;
    }
}
