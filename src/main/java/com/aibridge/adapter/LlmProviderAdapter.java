package com.aibridge.adapter;

import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatCompletionResponse;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.enums.ProviderName;

public interface LlmProviderAdapter {
    ProviderName getProviderName();

    ChatCompletionResponse complete(ChatCompletionRequest request, LlmConfig config);
}
