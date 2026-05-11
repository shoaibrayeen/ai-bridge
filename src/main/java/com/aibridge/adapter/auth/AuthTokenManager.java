package com.aibridge.adapter.auth;

import com.aibridge.cache.CacheProvider;
import com.aibridge.config.RedisConfig;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.enums.AuthType;
import com.aibridge.service.EncryptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class AuthTokenManager {

    private static final int IAM_TOKEN_CACHE_TTL_SECONDS = 55 * 60;

    private final HttpClient httpClient;

    @Inject
    CacheProvider cacheProvider;

    @Inject
    EncryptionService encryptionService;

    @Inject
    ObjectMapper objectMapper;

    public AuthTokenManager() {
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    public String getToken(LlmConfig config) {
        String key =
                RedisConfig.authTokenKey(
                        config.getProvider().getId().toString(), config.getId().toString());
        String cached = cacheProvider.get(key);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        String token = exchangeToken(config);
        cacheProvider.setex(key, IAM_TOKEN_CACHE_TTL_SECONDS, token);
        return token;
    }

    private String exchangeToken(LlmConfig config) {
        String decrypted = encryptionService.decrypt(config.getCredentialsEncrypted());
        JsonNode creds;
        try {
            creds = objectMapper.readTree(decrypted);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid credentials JSON for token exchange", e);
        }

        AuthType authType = config.getProvider().getAuthType();
        String authEndpoint = config.getProvider().getAuthEndpoint();
        if (authEndpoint == null || authEndpoint.isBlank()) {
            throw new IllegalStateException("authEndpoint is required for " + authType);
        }

        String formBody;
        switch (authType) {
            case IAM_TOKEN -> {
                String apiKey = textField(creds, "api_key");
                formBody =
                        formEncode(
                                "grant_type",
                                "urn:ibm:params:oauth:grant-type:apikey",
                                "apikey",
                                apiKey);
            }
            case OAUTH2 -> {
                String clientId = textField(creds, "client_id");
                String clientSecret = textField(creds, "client_secret");
                formBody =
                        formEncode(
                                "grant_type",
                                "client_credentials",
                                "client_id",
                                clientId,
                                "client_secret",
                                clientSecret);
            }
            default ->
                    throw new IllegalStateException(
                            "Token exchange not supported for auth type: " + authType);
        }

        HttpRequest request =
                HttpRequest.newBuilder(URI.create(authEndpoint))
                        .timeout(Duration.ofSeconds(60))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(formBody))
                        .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Token exchange failed: HTTP " + response.statusCode());
            }
            JsonNode body = objectMapper.readTree(response.body());
            JsonNode accessToken = body.get("access_token");
            if (accessToken == null || accessToken.isNull() || accessToken.asText().isEmpty()) {
                throw new IllegalStateException("Token response missing access_token");
            }
            return accessToken.asText();
        } catch (IOException e) {
            throw new IllegalStateException("Token exchange request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Token exchange interrupted", e);
        }
    }

    private static String textField(JsonNode creds, String field) {
        JsonNode n = creds.get(field);
        if (n == null || n.isNull() || n.asText().isBlank()) {
            throw new IllegalStateException("Missing credentials field: " + field);
        }
        return n.asText();
    }

    private static String formEncode(String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Pairs required");
        }
        return Stream.iterate(0, i -> i + 2)
                .limit(keyValues.length / 2L)
                .map(
                        i -> {
                            String k = keyValues[i];
                            String v = keyValues[i + 1];
                            return URLEncoder.encode(k, StandardCharsets.UTF_8)
                                    + "="
                                    + URLEncoder.encode(v, StandardCharsets.UTF_8);
                        })
                .collect(Collectors.joining("&"));
    }
}
