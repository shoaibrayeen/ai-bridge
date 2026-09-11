package com.aibridge.adapter;

import com.aibridge.dto.openai.ChatCompletionChunk;
import com.aibridge.dto.openai.Usage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Translates provider server-sent-event streams into OpenAI-format chunks.
 *
 * <p>Kept free of I/O on purpose: every method takes the raw SSE lines a provider emitted and
 * returns chunks, so the translation can be tested against recorded provider output without a
 * network call.
 */
public final class SseParsers {

    private static final String DATA_PREFIX = "data:";
    private static final String EVENT_PREFIX = "event:";

    private SseParsers() {
    }

    /** Payload of a {@code data:} line, or empty for any other line. */
    static Optional<String> dataPayload(String line) {
        if (line == null || !line.startsWith(DATA_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(line.substring(DATA_PREFIX.length()).trim());
    }

    static Optional<String> eventName(String line) {
        if (line == null || !line.startsWith(EVENT_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(line.substring(EVENT_PREFIX.length()).trim());
    }

    /**
     * Parses an OpenAI-compatible stream (OpenAI, Cerebras). Each {@code data:} line is already a
     * chunk in the target shape, so the only work is re-stamping the model with this gateway's
     * name and stopping at the {@code [DONE]} sentinel.
     *
     * <p>A malformed line is skipped rather than failing the stream: providers occasionally emit
     * keep-alive comments and blank lines, and one unparseable frame should not discard the
     * tokens the client has already received.
     */
    static List<ChatCompletionChunk> parseOpenAiCompatible(
            List<String> lines, String gatewayModelName, ObjectMapper mapper) {
        List<ChatCompletionChunk> chunks = new ArrayList<>();
        for (String line : lines) {
            Optional<String> payload = dataPayload(line);
            if (payload.isEmpty()) {
                continue;
            }
            String data = payload.get();
            if (data.isEmpty()) {
                continue;
            }
            if (ChatCompletionChunk.DONE.equals(data)) {
                break;
            }
            try {
                ChatCompletionChunk chunk = mapper.readValue(data, ChatCompletionChunk.class);
                chunk.setModel(gatewayModelName);
                chunks.add(chunk);
            } catch (Exception e) {
                // Not a chunk we understand — skip the frame, keep the stream.
                continue;
            }
        }
        return chunks;
    }

    /**
     * Parses Anthropic's stream, whose frames are a different shape entirely:
     * {@code message_start}, then {@code content_block_delta} frames carrying
     * {@code delta.text}, then {@code message_delta} with the stop reason and output tokens,
     * then {@code message_stop}.
     */
    static List<ChatCompletionChunk> parseAnthropic(
            List<String> lines, String id, String gatewayModelName, ObjectMapper mapper) {
        List<ChatCompletionChunk> chunks = new ArrayList<>();
        boolean roleEmitted = false;
        String stopReason = null;
        Integer inputTokens = null;
        Integer outputTokens = null;

        for (String line : lines) {
            Optional<String> payload = dataPayload(line);
            if (payload.isEmpty()) {
                continue;
            }
            String data = payload.get();
            if (data.isEmpty()) {
                continue;
            }
            JsonNode node;
            try {
                node = mapper.readTree(data);
            } catch (Exception e) {
                continue;
            }
            String type = node.path("type").asText("");

            switch (type) {
                case "message_start" -> {
                    if (!roleEmitted) {
                        chunks.add(ChatCompletionChunk.roleChunk(id, gatewayModelName));
                        roleEmitted = true;
                    }
                    JsonNode usage = node.path("message").path("usage");
                    if (usage.hasNonNull("input_tokens")) {
                        inputTokens = usage.get("input_tokens").asInt();
                    }
                }
                case "content_block_delta" -> {
                    String text = node.path("delta").path("text").asText("");
                    if (!text.isEmpty()) {
                        if (!roleEmitted) {
                            chunks.add(ChatCompletionChunk.roleChunk(id, gatewayModelName));
                            roleEmitted = true;
                        }
                        chunks.add(ChatCompletionChunk.contentChunk(id, gatewayModelName, text));
                    }
                }
                case "message_delta" -> {
                    JsonNode reason = node.path("delta").path("stop_reason");
                    if (!reason.isMissingNode() && !reason.isNull()) {
                        stopReason = mapStopReason(reason.asText());
                    }
                    JsonNode usage = node.path("usage");
                    if (usage.hasNonNull("output_tokens")) {
                        outputTokens = usage.get("output_tokens").asInt();
                    }
                }
                default -> {
                    // message_stop, content_block_start/stop, ping — nothing to translate.
                }
            }
        }

        if (!roleEmitted) {
            chunks.add(ChatCompletionChunk.roleChunk(id, gatewayModelName));
        }
        chunks.add(ChatCompletionChunk.finalChunk(
                id, gatewayModelName, stopReason, buildUsage(inputTokens, outputTokens)));
        return chunks;
    }

    /** Anthropic stop reasons do not share OpenAI's vocabulary. */
    static String mapStopReason(String anthropicReason) {
        if (anthropicReason == null) {
            return "stop";
        }
        return switch (anthropicReason) {
            case "max_tokens" -> "length";
            case "tool_use" -> "tool_calls";
            case "end_turn", "stop_sequence" -> "stop";
            default -> "stop";
        };
    }

    static Usage buildUsage(Integer promptTokens, Integer completionTokens) {
        if (promptTokens == null && completionTokens == null) {
            return null;
        }
        Usage usage = new Usage();
        usage.setPromptTokens(promptTokens);
        usage.setCompletionTokens(completionTokens);
        if (promptTokens != null && completionTokens != null) {
            usage.setTotalTokens(promptTokens + completionTokens);
        }
        return usage;
    }
}
