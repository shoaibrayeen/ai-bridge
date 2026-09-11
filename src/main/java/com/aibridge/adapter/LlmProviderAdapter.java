package com.aibridge.adapter;

import com.aibridge.dto.openai.ChatCompletionChunk;
import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatCompletionResponse;
import com.aibridge.dto.openai.Choice;
import com.aibridge.dto.openai.Usage;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.enums.ProviderName;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public interface LlmProviderAdapter {

    ProviderName getProviderName();

    ChatCompletionResponse complete(ChatCompletionRequest request, LlmConfig config);

    /**
     * Whether this adapter streams incrementally from the provider. When {@code false}, callers
     * still get a well-formed stream via {@link #completeStream} — it just arrives in one piece.
     */
    default boolean supportsNativeStreaming() {
        return false;
    }

    /**
     * Streams the completion as OpenAI-format chunks.
     *
     * <p>The default implementation makes the ordinary blocking call and emits the result as a
     * single content chunk. That keeps {@code stream: true} working against every provider,
     * including ones whose streaming protocol this gateway does not speak, at the cost of the
     * client waiting for the whole answer before the first token arrives.
     */
    default Stream<ChatCompletionChunk> completeStream(ChatCompletionRequest request, LlmConfig config) {
        ChatCompletionResponse response = complete(request, config);
        return singleShotStream(response, config);
    }

    /** Turns a completed response into the chunk sequence a streaming client expects. */
    static Stream<ChatCompletionChunk> singleShotStream(
            ChatCompletionResponse response, LlmConfig config) {
        String id = response != null && response.getId() != null
                ? response.getId()
                : "chatcmpl-" + UUID.randomUUID();
        String model = config.getGatewayModelName() != null
                ? config.getGatewayModelName()
                : config.getModelName();

        String content = extractContent(response);
        String finishReason = extractFinishReason(response);
        Usage usage = response == null ? null : response.getUsage();

        List<ChatCompletionChunk> chunks = new ArrayList<>(3);
        chunks.add(ChatCompletionChunk.roleChunk(id, model));
        if (content != null && !content.isEmpty()) {
            chunks.add(ChatCompletionChunk.contentChunk(id, model, content));
        }
        chunks.add(ChatCompletionChunk.finalChunk(id, model, finishReason, usage));
        return chunks.stream();
    }

    private static String extractContent(ChatCompletionResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return null;
        }
        Choice first = response.getChoices().get(0);
        return first == null || first.getMessage() == null ? null : first.getMessage().getContent();
    }

    private static String extractFinishReason(ChatCompletionResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return "stop";
        }
        Choice first = response.getChoices().get(0);
        return first == null || first.getFinishReason() == null ? "stop" : first.getFinishReason();
    }
}
