package com.aibridge.adapter;

import com.aibridge.dto.openai.ChatCompletionChunk;
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
import java.util.UUID;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ClaudeAdapter implements LlmProviderAdapter {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final HttpClient httpClient;

    @Inject
    EncryptionService encryptionService;

    @Inject
    ObjectMapper objectMapper;

    public ClaudeAdapter() {
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build();
    }

    @Override
    public ProviderName getProviderName() {
        return ProviderName.CLAUDE;
    }

    @Override
    public ChatCompletionResponse complete(ChatCompletionRequest request, LlmConfig config) {
        String apiKey = readApiKey(config);

        ObjectNode body = buildClaudeBody(request, config);
        String json = serialise(body);

        String url = config.getEndpointUrl().trim();
        HttpRequest httpRequest =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(60))
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", ANTHROPIC_VERSION)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 429) {
                throw new ProviderRateLimitException("Claude returned 429");
            }
            if (status >= 500) {
                throw new ProviderUnavailableException("Claude returned HTTP " + status);
            }
            String respBody = response.body();
            if (respBody == null || respBody.isBlank()) {
                return ChatCompletionResponse.empty(config.getModelName());
            }
            return mapClaudeResponse(respBody, config.getModelName());
        } catch (IOException e) {
            throw new ProviderUnavailableException("Claude request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException("Claude request interrupted", e);
        }
    }

    private ChatCompletionResponse mapClaudeResponse(String respBody, String modelName)
            throws IOException {
        JsonNode root = objectMapper.readTree(respBody);

        String id = root.path("id").asText("claude-msg");
        String model = root.path("model").asText(modelName);
        String stopReason = root.path("stop_reason").asText("stop");
        String finishReason = mapFinishReason(stopReason);

        String text = "";
        JsonNode content = root.path("content");
        if (content.isArray() && content.size() > 0) {
            JsonNode first = content.get(0);
            if (first.path("type").asText("").equals("text")) {
                text = first.path("text").asText("");
            }
        }

        ChatMessage assistant = new ChatMessage();
        assistant.setRole("assistant");
        assistant.setContent(text);

        Choice choice = new Choice();
        choice.setIndex(0);
        choice.setMessage(assistant);
        choice.setFinishReason(finishReason);

        Usage usage = new Usage();
        JsonNode usageNode = root.path("usage");
        int inTok = usageNode.path("input_tokens").asInt(0);
        int outTok = usageNode.path("output_tokens").asInt(0);
        usage.setPromptTokens(inTok);
        usage.setCompletionTokens(outTok);
        usage.setTotalTokens(inTok + outTok);

        ChatCompletionResponse out = new ChatCompletionResponse();
        out.setId(id);
        out.setObject("chat.completion");
        out.setCreated(System.currentTimeMillis() / 1000);
        out.setModel(model);
        out.setChoices(List.of(choice));
        out.setUsage(usage);
        return out;
    }

    private static String mapFinishReason(String stopReason) {
        if (stopReason == null || stopReason.isEmpty()) {
            return "stop";
        }
        if ("end_turn".equals(stopReason)) {
            return "stop";
        }
        return stopReason;
    }

    @Override
    public boolean supportsNativeStreaming() {
        return true;
    }

    @Override
    public Stream<ChatCompletionChunk> completeStream(ChatCompletionRequest request, LlmConfig config) {
        String apiKey = readApiKey(config);
        ObjectNode body = buildClaudeBody(request, config);
        body.put("stream", true);
        String json = serialise(body);

        HttpRequest httpRequest =
                HttpRequest.newBuilder(URI.create(config.getEndpointUrl().trim()))
                        .timeout(Duration.ofSeconds(300))
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", ANTHROPIC_VERSION)
                        .header("Content-Type", "application/json")
                        .header("Accept", "text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();

        try {
            HttpResponse<Stream<String>> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
            int status = response.statusCode();
            if (status == 429) {
                throw new ProviderRateLimitException("Claude returned 429");
            }
            if (status >= 400) {
                throw new ProviderUnavailableException("Claude returned HTTP " + status);
            }
            String gatewayModel = config.getGatewayModelName() != null
                    ? config.getGatewayModelName()
                    : config.getModelName();
            String id = "chatcmpl-" + UUID.randomUUID();
            try (Stream<String> lines = response.body()) {
                return SseParsers.parseAnthropic(lines.toList(), id, gatewayModel, objectMapper)
                        .stream();
            }
        } catch (IOException e) {
            throw new ProviderUnavailableException("Claude stream failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException("Claude stream interrupted", e);
        }
    }

    /**
     * Builds Anthropic's request shape: the system prompt is lifted out of {@code messages} into
     * its own field, and each remaining message becomes a single text content block.
     */
    private ObjectNode buildClaudeBody(ChatCompletionRequest request, LlmConfig config) {
        List<String> systemParts = new ArrayList<>();
        ArrayNode claudeMessages = objectMapper.createArrayNode();
        if (request.getMessages() != null) {
            for (ChatMessage m : request.getMessages()) {
                if (m == null || m.getRole() == null) {
                    continue;
                }
                String role = m.getRole().toLowerCase();
                String content = m.getContent() == null ? "" : m.getContent();
                if ("system".equals(role)) {
                    systemParts.add(content);
                } else if ("user".equals(role) || "assistant".equals(role)) {
                    ObjectNode msg = objectMapper.createObjectNode();
                    msg.put("role", role);
                    ArrayNode contentArr = objectMapper.createArrayNode();
                    ObjectNode textBlock = objectMapper.createObjectNode();
                    textBlock.put("type", "text");
                    textBlock.put("text", content);
                    contentArr.add(textBlock);
                    msg.set("content", contentArr);
                    claudeMessages.add(msg);
                }
            }
        }
        if (claudeMessages.isEmpty()) {
            ObjectNode msg = objectMapper.createObjectNode();
            msg.put("role", "user");
            ArrayNode contentArr = objectMapper.createArrayNode();
            ObjectNode textBlock = objectMapper.createObjectNode();
            textBlock.put("type", "text");
            textBlock.put("text", "");
            contentArr.add(textBlock);
            msg.set("content", contentArr);
            claudeMessages.add(msg);
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.getModelName());
        int maxTokens =
                request.getMaxTokens() != null && request.getMaxTokens() > 0
                        ? request.getMaxTokens()
                        : 1024;
        body.put("max_tokens", maxTokens);
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        if (!systemParts.isEmpty()) {
            body.put("system", String.join("\n\n", systemParts));
        }
        body.set("messages", claudeMessages);
        return body;
    }

    private String serialise(ObjectNode body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize Claude request", e);
        }
    }

    private String readApiKey(LlmConfig config) {
        String decrypted = encryptionService.decrypt(config.getCredentialsEncrypted());
        JsonNode creds;
        try {
            creds = objectMapper.readTree(decrypted);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid credentials JSON", e);
        }
        JsonNode apiKeyNode = creds.get("api_key");
        if (apiKeyNode == null || apiKeyNode.asText().isBlank()) {
            throw new IllegalStateException("Missing api_key in credentials");
        }
        return apiKeyNode.asText();
    }
}
