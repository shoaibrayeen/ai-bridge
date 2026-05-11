package com.aibridge.adapter;

import com.aibridge.adapter.auth.AuthTokenManager;
import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatCompletionResponse;
import com.aibridge.dto.openai.ChatMessage;
import com.aibridge.dto.openai.Choice;
import com.aibridge.dto.openai.Usage;
import com.aibridge.exception.ProviderRateLimitException;
import com.aibridge.exception.ProviderUnavailableException;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.enums.ProviderName;
import com.aibridge.service.EncryptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@ApplicationScoped
public class WatsonXAdapter implements LlmProviderAdapter {

    private final HttpClient httpClient;

    @Inject
    AuthTokenManager authTokenManager;

    @Inject
    EncryptionService encryptionService;

    @Inject
    ObjectMapper objectMapper;

    public WatsonXAdapter() {
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build();
    }

    @Override
    public ProviderName getProviderName() {
        return ProviderName.WATSONX;
    }

    @Override
    public ChatCompletionResponse complete(ChatCompletionRequest request, LlmConfig config) {
        // Ensure credentials can be decrypted (validates storage); token exchange uses same JSON.
        encryptionService.decrypt(config.getCredentialsEncrypted());

        String projectId = parseProjectId(config.getExtraParams());
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("extra_params must contain project_id for WatsonX");
        }

        String token = authTokenManager.getToken(config);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model_id", config.getModelName());
        body.put("project_id", projectId);

        ArrayNode messages = objectMapper.createArrayNode();
        if (request.getMessages() != null) {
            for (ChatMessage m : request.getMessages()) {
                if (m == null || m.getRole() == null) {
                    continue;
                }
                ObjectNode msg = objectMapper.createObjectNode();
                msg.put("role", m.getRole());
                msg.put("content", m.getContent() == null ? "" : m.getContent());
                messages.add(msg);
            }
        }
        body.set("messages", messages);

        ObjectNode parameters = objectMapper.createObjectNode();
        if (request.getMaxTokens() != null) {
            parameters.put("max_new_tokens", request.getMaxTokens());
        }
        if (request.getTemperature() != null) {
            parameters.put("temperature", request.getTemperature());
        }
        body.set("parameters", parameters);

        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize WatsonX request", e);
        }

        String url = config.getEndpointUrl().trim();
        HttpRequest httpRequest =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(60))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 429) {
                throw new ProviderRateLimitException("WatsonX returned 429");
            }
            if (status >= 500) {
                throw new ProviderUnavailableException("WatsonX returned HTTP " + status);
            }
            String respBody = response.body();
            if (respBody == null || respBody.isBlank()) {
                return ChatCompletionResponse.empty(config.getModelName());
            }
            return mapWatsonXResponse(respBody, config.getModelName());
        } catch (IOException e) {
            throw new ProviderUnavailableException("WatsonX request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException("WatsonX request interrupted", e);
        }
    }

    private String parseProjectId(String extraParams) {
        if (extraParams == null || extraParams.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(extraParams);
            JsonNode pid = node.get("project_id");
            return pid != null && !pid.isNull() ? pid.asText(null) : null;
        } catch (IOException e) {
            throw new IllegalStateException("Invalid extra_params JSON for WatsonX", e);
        }
    }

    private ChatCompletionResponse mapWatsonXResponse(String respBody, String modelName)
            throws IOException {
        JsonNode root = objectMapper.readTree(respBody);

        JsonNode choicesNode = root.get("choices");
        if (choicesNode == null || !choicesNode.isArray() || choicesNode.isEmpty()) {
            return ChatCompletionResponse.empty(modelName);
        }

        JsonNode first = choicesNode.get(0);
        JsonNode message = first.get("message");
        String role = "assistant";
        String content = "";
        if (message != null) {
            if (message.hasNonNull("role")) {
                role = message.get("role").asText(role);
            }
            JsonNode contentNode = message.get("content");
            content = extractWatsonXContent(contentNode);
        }

        String finishReason =
                first.path("finish_reason").asText(first.path("stop_reason").asText("stop"));

        ChatMessage assistant = new ChatMessage();
        assistant.setRole(role);
        assistant.setContent(content);

        Choice choice = new Choice();
        choice.setIndex(0);
        choice.setMessage(assistant);
        choice.setFinishReason(finishReason);

        Usage usage = new Usage();
        JsonNode usageNode = root.get("usage");
        if (usageNode != null && !usageNode.isNull()) {
            int prompt = usageNode.path("prompt_tokens").asInt(usageNode.path("input_tokens").asInt(0));
            int completion =
                    usageNode
                            .path("completion_tokens")
                            .asInt(usageNode.path("generated_tokens").asInt(0));
            usage.setPromptTokens(prompt);
            usage.setCompletionTokens(completion);
            usage.setTotalTokens(
                    usageNode.path("total_tokens").asInt(prompt + completion));
        } else {
            usage.setPromptTokens(0);
            usage.setCompletionTokens(0);
            usage.setTotalTokens(0);
        }

        ChatCompletionResponse out = new ChatCompletionResponse();
        out.setId(root.path("id").asText("watsonx-chat"));
        out.setObject("chat.completion");
        out.setCreated(root.path("created").asLong(System.currentTimeMillis() / 1000));
        out.setModel(root.path("model_id").asText(modelName));
        out.setChoices(List.of(choice));
        out.setUsage(usage);
        return out;
    }

    private String extractWatsonXContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : contentNode) {
                if (part.has("text")) {
                    sb.append(part.get("text").asText(""));
                } else if (part.isTextual()) {
                    sb.append(part.asText());
                }
            }
            return sb.toString();
        }
        return contentNode.asText("");
    }
}
