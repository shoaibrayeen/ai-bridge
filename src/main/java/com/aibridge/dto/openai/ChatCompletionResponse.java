package com.aibridge.dto.openai;

import com.aibridge.dto.lineage.CompletionLineage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionResponse {

    private String id;

    private String object = "chat.completion";

    private Long created;

    private String model;

    private List<Choice> choices;

    private Usage usage;

    public ChatCompletionResponse() {
    }

    public static ChatCompletionResponse empty(String model) {
        ChatMessage message = new ChatMessage();
        message.setRole("assistant");
        message.setContent("");

        Choice choice = new Choice();
        choice.setIndex(0);
        choice.setMessage(message);
        choice.setFinishReason("stop");

        Usage usage = new Usage();
        usage.setPromptTokens(0);
        usage.setCompletionTokens(0);
        usage.setTotalTokens(0);

        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setId("chatcmpl-empty");
        response.setObject("chat.completion");
        response.setCreated(System.currentTimeMillis() / 1000);
        response.setModel(model);
        response.setChoices(List.of(choice));
        response.setUsage(usage);
        return response;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("object")
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

    /**
     * Gateway routing trail for this completion. Populated on every call and always logged;
     * stripped from the wire response unless the caller sent {@code X-Include-Lineage: true},
     * so the default payload stays a plain OpenAI response.
     */
    @JsonProperty("x_aibridge_lineage")
    private CompletionLineage lineage;

    public CompletionLineage getLineage() {
        return lineage;
    }

    public void setLineage(CompletionLineage lineage) {
        this.lineage = lineage;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }
}
