package com.aibridge.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aibridge.dto.openai.ChatCompletionChunk;
import com.aibridge.dto.openai.Usage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SseParsersTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // ---------- line framing ----------

    @Test
    void dataPayload_stripsPrefixAndWhitespace() {
        assertEquals("hello", SseParsers.dataPayload("data: hello").orElseThrow());
        assertEquals("hello", SseParsers.dataPayload("data:hello").orElseThrow());
        assertEquals("hello", SseParsers.dataPayload("data:   hello   ").orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "event: ping", ": keep-alive", "id: 1", "  data: indented"})
    void dataPayload_ignoresNonDataLines(String line) {
        assertTrue(SseParsers.dataPayload(line).isEmpty());
    }

    @Test
    void eventName_readsEventLines() {
        assertEquals("content_block_delta",
                SseParsers.eventName("event: content_block_delta").orElseThrow());
        assertTrue(SseParsers.eventName("data: {}").isEmpty());
    }

    // ---------- OpenAI-compatible ----------

    @Test
    void openAi_parsesARealStreamAndRestampsTheModel() {
        List<String> lines = List.of(
            "data: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"}}]}",
            "",
            "data: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hel\"}}]}",
            "",
            "data: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"lo\"}}]}",
            "",
            "data: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}",
            "",
            "data: [DONE]");

        List<ChatCompletionChunk> chunks =
                SseParsers.parseOpenAiCompatible(lines, "ai-bridge-1-gpt-4o", mapper);

        assertEquals(4, chunks.size());
        // Every chunk carries the gateway identity, never the provider's own model id.
        assertTrue(chunks.stream().allMatch(c -> "ai-bridge-1-gpt-4o".equals(c.getModel())));
        assertEquals("assistant", chunks.get(0).getChoices().get(0).getDelta().getRole());
        assertEquals("Hel", chunks.get(1).getChoices().get(0).getDelta().getContent());
        assertEquals("lo", chunks.get(2).getChoices().get(0).getDelta().getContent());
        assertEquals("stop", chunks.get(3).getChoices().get(0).getFinishReason());
    }

    @Test
    void openAi_stopsAtDoneAndIgnoresAnythingAfter() {
        List<String> lines = List.of(
            "data: {\"choices\":[{\"delta\":{\"content\":\"a\"}}]}",
            "data: [DONE]",
            "data: {\"choices\":[{\"delta\":{\"content\":\"should-not-appear\"}}]}");

        List<ChatCompletionChunk> chunks = SseParsers.parseOpenAiCompatible(lines, "m", mapper);

        assertEquals(1, chunks.size());
        assertEquals("a", chunks.get(0).getChoices().get(0).getDelta().getContent());
    }

    @Test
    void openAi_skipsMalformedFramesWithoutLosingTheStream() {
        List<String> lines = List.of(
            "data: {\"choices\":[{\"delta\":{\"content\":\"a\"}}]}",
            "data: {not json at all",
            ": keep-alive comment",
            "data: ",
            "data: {\"choices\":[{\"delta\":{\"content\":\"b\"}}]}",
            "data: [DONE]");

        List<ChatCompletionChunk> chunks = SseParsers.parseOpenAiCompatible(lines, "m", mapper);

        assertEquals(2, chunks.size());
        assertEquals("a", chunks.get(0).getChoices().get(0).getDelta().getContent());
        assertEquals("b", chunks.get(1).getChoices().get(0).getDelta().getContent());
    }

    @Test
    void openAi_emptyStreamYieldsNoChunks() {
        assertTrue(SseParsers.parseOpenAiCompatible(List.of(), "m", mapper).isEmpty());
        assertTrue(SseParsers.parseOpenAiCompatible(List.of("data: [DONE]"), "m", mapper).isEmpty());
    }

    @Test
    void openAi_carriesUsageThrough() {
        List<String> lines = List.of(
            "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":9,\"completion_tokens\":5,\"total_tokens\":14}}",
            "data: [DONE]");

        List<ChatCompletionChunk> chunks = SseParsers.parseOpenAiCompatible(lines, "m", mapper);

        Usage usage = chunks.get(0).getUsage();
        assertNotNull(usage);
        assertEquals(14, usage.getTotalTokens());
    }

    // ---------- Anthropic ----------

    @Test
    void anthropic_translatesARealStreamIntoOpenAiChunks() {
        List<String> lines = List.of(
            "event: message_start",
            "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"usage\":{\"input_tokens\":12}}}",
            "",
            "event: content_block_start",
            "data: {\"type\":\"content_block_start\",\"index\":0}",
            "",
            "event: content_block_delta",
            "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hel\"}}",
            "",
            "event: content_block_delta",
            "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"lo\"}}",
            "",
            "event: message_delta",
            "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":7}}",
            "",
            "event: message_stop",
            "data: {\"type\":\"message_stop\"}");

        List<ChatCompletionChunk> chunks =
                SseParsers.parseAnthropic(lines, "chatcmpl-x", "ai-bridge-1-claude-sonnet-6", mapper);

        // role, "Hel", "lo", terminating chunk
        assertEquals(4, chunks.size());
        assertEquals("assistant", chunks.get(0).getChoices().get(0).getDelta().getRole());
        assertEquals("Hel", chunks.get(1).getChoices().get(0).getDelta().getContent());
        assertEquals("lo", chunks.get(2).getChoices().get(0).getDelta().getContent());

        ChatCompletionChunk last = chunks.get(3);
        assertEquals("stop", last.getChoices().get(0).getFinishReason());
        assertEquals(12, last.getUsage().getPromptTokens());
        assertEquals(7, last.getUsage().getCompletionTokens());
        assertEquals(19, last.getUsage().getTotalTokens());
        assertTrue(chunks.stream().allMatch(c -> "ai-bridge-1-claude-sonnet-6".equals(c.getModel())));
    }

    @Test
    void anthropic_alwaysEmitsARoleChunkFirstEvenWithoutMessageStart() {
        List<String> lines = List.of(
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"text\":\"hi\"}}");

        List<ChatCompletionChunk> chunks = SseParsers.parseAnthropic(lines, "id", "m", mapper);

        assertEquals("assistant", chunks.get(0).getChoices().get(0).getDelta().getRole());
        assertEquals("hi", chunks.get(1).getChoices().get(0).getDelta().getContent());
    }

    @Test
    void anthropic_alwaysTerminatesEvenOnAnEmptyStream() {
        List<ChatCompletionChunk> chunks = SseParsers.parseAnthropic(List.of(), "id", "m", mapper);

        // A client waiting for a finish_reason must not hang on a provider that said nothing.
        assertEquals(2, chunks.size());
        assertEquals("stop", chunks.get(1).getChoices().get(0).getFinishReason());
        assertNull(chunks.get(1).getUsage());
    }

    @Test
    void anthropic_skipsEmptyTextDeltas() {
        List<String> lines = List.of(
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"text\":\"\"}}",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"text\":\"x\"}}");

        List<ChatCompletionChunk> chunks = SseParsers.parseAnthropic(lines, "id", "m", mapper);

        assertEquals(3, chunks.size());
        assertEquals("x", chunks.get(1).getChoices().get(0).getDelta().getContent());
    }

    @ParameterizedTest
    @CsvSource({
        "end_turn, stop",
        "stop_sequence, stop",
        "max_tokens, length",
        "tool_use, tool_calls",
        "something_new, stop"
    })
    void anthropic_mapsStopReasonsToOpenAiVocabulary(String anthropic, String expected) {
        assertEquals(expected, SseParsers.mapStopReason(anthropic));
    }

    @Test
    void anthropic_nullStopReasonDefaultsToStop() {
        assertEquals("stop", SseParsers.mapStopReason(null));
    }

    // ---------- usage assembly ----------

    @Test
    void buildUsage_totalsOnlyWhenBothHalvesAreKnown() {
        assertEquals(30, SseParsers.buildUsage(10, 20).getTotalTokens());
        assertNull(SseParsers.buildUsage(10, null).getTotalTokens());
        assertEquals(10, SseParsers.buildUsage(10, null).getPromptTokens());
        assertNull(SseParsers.buildUsage(null, null));
    }
}
