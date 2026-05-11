package com.aibridge.adapter;

import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatCompletionResponse;
import com.aibridge.exception.ProviderRateLimitException;
import com.aibridge.exception.ProviderUnavailableException;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.enums.ProviderName;
import com.aibridge.service.EncryptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@ApplicationScoped
public class OpenAiAdapter implements LlmProviderAdapter {

    private final HttpClient httpClient;

    @Inject
    EncryptionService encryptionService;

    @Inject
    ObjectMapper objectMapper;

    public OpenAiAdapter() {
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build();
    }

    @Override
    public ProviderName getProviderName() {
        return ProviderName.OPENAI;
    }

    @Override
    public ChatCompletionResponse complete(ChatCompletionRequest request, LlmConfig config) {
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
        String apiKey = apiKeyNode.asText();

        String url = resolveChatCompletionsUrl(config.getEndpointUrl());

        ObjectNode body = objectMapper.valueToTree(request);
        body.remove("stream");

        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize request", e);
        }

        HttpRequest httpRequest =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(60))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 429) {
                throw new ProviderRateLimitException("OpenAI-compatible provider returned 429");
            }
            if (status >= 500) {
                throw new ProviderUnavailableException(
                        "OpenAI-compatible provider returned HTTP " + status);
            }
            String respBody = response.body();
            if (respBody == null || respBody.isBlank()) {
                return ChatCompletionResponse.empty(config.getModelName());
            }
            return objectMapper.readValue(respBody, ChatCompletionResponse.class);
        } catch (IOException e) {
            throw new ProviderUnavailableException("OpenAI request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException("OpenAI request interrupted", e);
        }
    }

    static String resolveChatCompletionsUrl(String endpointUrl) {
        String base = endpointUrl == null ? "" : endpointUrl.trim();
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        if (base.endsWith("/")) {
            return base + "v1/chat/completions";
        }
        return base + "/v1/chat/completions";
    }
}
