package com.aibridge.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aibridge.dto.openai.ChatCompletionChunk;
import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatCompletionResponse;
import com.aibridge.dto.openai.ChatMessage;
import com.aibridge.dto.openai.Choice;
import com.aibridge.dto.openai.Usage;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.enums.ProviderName;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The fallback path: an adapter that cannot stream natively must still produce a well-formed
 * stream, so {@code stream: true} works against every provider.
 */
class SingleShotStreamTest {

    @Test
    void fallbackAdapterStillProducesAValidStream() {
        LlmConfig config = config();
        LlmProviderAdapter adapter = fallbackAdapter(responseWith("Hello there", "stop", usage(4, 3, 7)));

        List<ChatCompletionChunk> chunks =
                adapter.completeStream(new ChatCompletionRequest(), config).toList();

        assertEquals(3, chunks.size());
        assertEquals("assistant", chunks.get(0).getChoices().get(0).getDelta().getRole());
        assertEquals("Hello there", chunks.get(1).getChoices().get(0).getDelta().getContent());
        assertEquals("stop", chunks.get(2).getChoices().get(0).getFinishReason());
        assertEquals(7, chunks.get(2).getUsage().getTotalTokens());
    }

    @Test
    void fallbackAdapterReportsItDoesNotStreamNatively() {
        assertTrue(!fallbackAdapter(responseWith("x", "stop", null)).supportsNativeStreaming());
    }

    @Test
    void everyChunkCarriesTheGatewayModelName() {
        LlmConfig config = config();
        List<ChatCompletionChunk> chunks = fallbackAdapter(responseWith("x", "stop", null))
                .completeStream(new ChatCompletionRequest(), config)
                .toList();

        assertTrue(chunks.stream().allMatch(c -> "ai-bridge-1-m1".equals(c.getModel())));
    }

    @Test
    void emptyContentStillTerminatesTheStream() {
        List<ChatCompletionChunk> chunks = fallbackAdapter(responseWith("", "stop", null))
                .completeStream(new ChatCompletionRequest(), config())
                .toList();

        // No content chunk, but a client waiting for finish_reason must still get one.
        assertEquals(2, chunks.size());
        assertEquals("stop", chunks.get(1).getChoices().get(0).getFinishReason());
    }

    @Test
    void aResponseWithNoChoicesStillTerminates() {
        ChatCompletionResponse empty = new ChatCompletionResponse();
        empty.setChoices(List.of());

        List<ChatCompletionChunk> chunks =
                fallbackAdapter(empty).completeStream(new ChatCompletionRequest(), config()).toList();

        assertEquals(2, chunks.size());
        assertEquals("stop", chunks.get(1).getChoices().get(0).getFinishReason());
        assertNull(chunks.get(1).getUsage());
    }

    @Test
    void chunkIdIsStableAcrossTheStream() {
        ChatCompletionResponse response = responseWith("x", "stop", null);
        response.setId("chatcmpl-fixed");

        List<ChatCompletionChunk> chunks =
                fallbackAdapter(response).completeStream(new ChatCompletionRequest(), config()).toList();

        assertTrue(chunks.stream().allMatch(c -> "chatcmpl-fixed".equals(c.getId())));
    }

    @Test
    void anIdIsSynthesisedWhenTheProviderGivesNone() {
        ChatCompletionResponse response = responseWith("x", "stop", null);
        response.setId(null);

        List<ChatCompletionChunk> chunks =
                fallbackAdapter(response).completeStream(new ChatCompletionRequest(), config()).toList();

        assertNotNull(chunks.get(0).getId());
        assertTrue(chunks.get(0).getId().startsWith("chatcmpl-"));
    }

    @Test
    void aNullFinishReasonBecomesStop() {
        List<ChatCompletionChunk> chunks = fallbackAdapter(responseWith("x", null, null))
                .completeStream(new ChatCompletionRequest(), config())
                .toList();

        assertEquals("stop", chunks.get(chunks.size() - 1).getChoices().get(0).getFinishReason());
    }

    private static LlmProviderAdapter fallbackAdapter(ChatCompletionResponse response) {
        return new LlmProviderAdapter() {
            @Override
            public ProviderName getProviderName() {
                return ProviderName.WATSONX;
            }

            @Override
            public ChatCompletionResponse complete(ChatCompletionRequest request, LlmConfig config) {
                return response;
            }
        };
    }

    private static LlmConfig config() {
        LlmConfig c = new LlmConfig();
        c.setModelName("m1");
        c.setGatewayModelName("ai-bridge-1-m1");
        return c;
    }

    private static ChatCompletionResponse responseWith(String content, String finishReason, Usage usage) {
        ChatMessage message = new ChatMessage();
        message.setRole("assistant");
        message.setContent(content);
        Choice choice = new Choice();
        choice.setMessage(message);
        choice.setFinishReason(finishReason);
        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setChoices(List.of(choice));
        response.setUsage(usage);
        return response;
    }

    private static Usage usage(int prompt, int completion, int total) {
        Usage u = new Usage();
        u.setPromptTokens(prompt);
        u.setCompletionTokens(completion);
        u.setTotalTokens(total);
        return u;
    }
}
