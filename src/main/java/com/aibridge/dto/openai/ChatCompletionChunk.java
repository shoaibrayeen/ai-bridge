package com.aibridge.dto.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * One server-sent event of a streaming completion, in the OpenAI
 * {@code chat.completion.chunk} shape. A stream is a sequence of these followed by the literal
 * sentinel {@code data: [DONE]}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionChunk {

    /** Sentinel an OpenAI-compatible stream ends with. */
    public static final String DONE = "[DONE]";

    private String id;

    private String object = "chat.completion.chunk";

    private Long created;

    private String model;

    private List<ChunkChoice> choices;

    private Usage usage;

    /**
     * First chunk of a stream: carries the assistant role and no content, which is what OpenAI
     * clients expect before any text arrives.
     */
    public static ChatCompletionChunk roleChunk(String id, String model) {
        ChatCompletionChunk chunk = base(id, model);
        chunk.choices = List.of(ChunkChoice.ofRole("assistant"));
        return chunk;
    }

    /** A chunk carrying a fragment of assistant text. */
    public static ChatCompletionChunk contentChunk(String id, String model, String content) {
        ChatCompletionChunk chunk = base(id, model);
        chunk.choices = List.of(ChunkChoice.ofContent(content));
        return chunk;
    }

    /** Final chunk before the sentinel, carrying the finish reason and any usage the provider gave. */
    public static ChatCompletionChunk finalChunk(String id, String model, String finishReason, Usage usage) {
        ChatCompletionChunk chunk = base(id, model);
        chunk.choices = List.of(ChunkChoice.ofFinish(finishReason == null ? "stop" : finishReason));
        chunk.usage = usage;
        return chunk;
    }

    private static ChatCompletionChunk base(String id, String model) {
        ChatCompletionChunk chunk = new ChatCompletionChunk();
        chunk.id = id;
        chunk.model = model;
        chunk.created = Instant.now().getEpochSecond();
        return chunk;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public Long getCreated() {
        return created;
    }

    public void setCreated(Long created) {
        this.created = created;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<ChunkChoice> getChoices() {
        return choices;
    }

    public void setChoices(List<ChunkChoice> choices) {
        this.choices = choices;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    /** A choice inside a chunk. Unlike a non-streaming choice, it carries a {@code delta}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChunkChoice {

        private int index;

        private Delta delta;

        @JsonProperty("finish_reason")
        private String finishReason;

        static ChunkChoice ofRole(String role) {
            ChunkChoice c = new ChunkChoice();
            c.delta = Delta.ofRole(role);
            return c;
        }

        static ChunkChoice ofContent(String content) {
            ChunkChoice c = new ChunkChoice();
            c.delta = Delta.ofContent(content);
            return c;
        }

        static ChunkChoice ofFinish(String finishReason) {
            ChunkChoice c = new ChunkChoice();
            c.delta = new Delta();
            c.finishReason = finishReason;
            return c;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public Delta getDelta() {
            return delta;
        }

        public void setDelta(Delta delta) {
            this.delta = delta;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }
    }

    /** The incremental part of a message. Both fields are absent on the terminating chunk. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Delta {

        private String role;

        private String content;

        static Delta ofRole(String role) {
            Delta d = new Delta();
            d.role = role;
            return d;
        }

        static Delta ofContent(String content) {
            Delta d = new Delta();
            d.content = content;
            return d;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
