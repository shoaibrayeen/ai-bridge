package com.aibridge.adapter;

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
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@ApplicationScoped
public class BedrockAdapter implements LlmProviderAdapter {

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String SERVICE = "bedrock";
    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US)
                    .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US).withZone(ZoneOffset.UTC);

    private static final Pattern RUNTIME_HOST_REGION =
            Pattern.compile("bedrock-runtime\\.([a-z0-9-]+)\\.amazonaws\\.com");

    private final HttpClient httpClient;

    @Inject
    EncryptionService encryptionService;

    @Inject
    ObjectMapper objectMapper;

    public BedrockAdapter() {
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build();
    }

    @Override
    public ProviderName getProviderName() {
        return ProviderName.BEDROCK;
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

        String accessKey =
                firstNonBlank(creds, "Missing AWS access key in credentials", "aws_access_key", "aws_access_key_id", "access_key_id");
        String secretKey =
                firstNonBlank(
                        creds,
                        "Missing AWS secret key in credentials",
                        "aws_secret_key",
                        "aws_secret_access_key",
                        "secret_access_key");
        String sessionToken = optionalText(creds, "aws_session_token");

        String endpoint = config.getEndpointUrl().trim();
        URI uri = URI.create(endpoint);
        String region = resolveRegion(uri.getHost(), creds, config.getExtraParams());
        if (region == null || region.isBlank()) {
            throw new IllegalStateException("Could not resolve AWS region for Bedrock");
        }

        ObjectNode body = buildConverseBody(request);

        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize Bedrock request", e);
        }
        byte[] bodyBytes = json.getBytes(StandardCharsets.UTF_8);

        Instant now = Instant.now();
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = DATE_STAMP.format(now);
        String payloadHash = sha256Hex(bodyBytes);

        String host = uri.getHost();
        String canonicalUri = uri.getPath().isEmpty() ? "/" : uri.getPath();
        String canonicalQueryString = normalizeQuery(uri.getRawQuery());

        StringBuilder canonicalHeaders = new StringBuilder();
        canonicalHeaders.append("content-type:application/json\n");
        canonicalHeaders.append("host:").append(host).append('\n');
        canonicalHeaders.append("x-amz-content-sha256:").append(payloadHash).append('\n');
        canonicalHeaders.append("x-amz-date:").append(amzDate).append('\n');
        String signedHeaders = "content-type;host;x-amz-content-sha256;x-amz-date";
        if (sessionToken != null && !sessionToken.isEmpty()) {
            canonicalHeaders.append("x-amz-security-token:").append(sessionToken).append('\n');
            signedHeaders = "content-type;host;x-amz-content-sha256;x-amz-date;x-amz-security-token";
        }
        canonicalHeaders.append('\n');

        String canonicalRequest =
                "POST\n"
                        + canonicalUri
                        + "\n"
                        + canonicalQueryString
                        + "\n"
                        + canonicalHeaders
                        + signedHeaders
                        + "\n"
                        + payloadHash;

        String credentialScope = dateStamp + "/" + region + "/" + SERVICE + "/aws4_request";
        String stringToSign =
                ALGORITHM
                        + "\n"
                        + amzDate
                        + "\n"
                        + credentialScope
                        + "\n"
                        + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        byte[] signingKey = deriveSigningKey(secretKey, dateStamp, region, SERVICE);
        String signature = hmacHex(signingKey, stringToSign);

        String authorization =
                ALGORITHM
                        + " Credential="
                        + accessKey
                        + "/"
                        + credentialScope
                        + ", SignedHeaders="
                        + signedHeaders
                        + ", Signature="
                        + signature;

        HttpRequest.Builder rb =
                HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(60))
                        .header("Content-Type", "application/json")
                        .header("Host", host)
                        .header("x-amz-date", amzDate)
                        .header("x-amz-content-sha256", payloadHash)
                        .header("Authorization", authorization);
        if (sessionToken != null && !sessionToken.isEmpty()) {
            rb.header("x-amz-security-token", sessionToken);
        }
        HttpRequest httpRequest = rb.POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes)).build();

        try {
            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 429) {
                throw new ProviderRateLimitException("Bedrock returned 429");
            }
            if (status >= 500) {
                throw new ProviderUnavailableException("Bedrock returned HTTP " + status);
            }
            String respBody = response.body();
            if (respBody == null || respBody.isBlank()) {
                return ChatCompletionResponse.empty(config.getModelName());
            }
            return mapConverseResponse(respBody, config.getModelName());
        } catch (IOException e) {
            throw new ProviderUnavailableException("Bedrock request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException("Bedrock request interrupted", e);
        }
    }

    private ObjectNode buildConverseBody(ChatCompletionRequest request) {
        List<String> systemTexts = new ArrayList<>();
        ArrayNode messages = objectMapper.createArrayNode();

        if (request.getMessages() != null) {
            for (ChatMessage m : request.getMessages()) {
                if (m == null || m.getRole() == null) {
                    continue;
                }
                String role = m.getRole().toLowerCase(Locale.ROOT);
                String text = m.getContent() == null ? "" : m.getContent();
                if ("system".equals(role)) {
                    systemTexts.add(text);
                } else if ("user".equals(role) || "assistant".equals(role)) {
                    ObjectNode msg = objectMapper.createObjectNode();
                    msg.put("role", role);
                    ArrayNode content = objectMapper.createArrayNode();
                    ObjectNode textBlock = objectMapper.createObjectNode();
                    textBlock.put("text", text);
                    content.add(textBlock);
                    msg.set("content", content);
                    messages.add(msg);
                } else {
                    ObjectNode msg = objectMapper.createObjectNode();
                    msg.put("role", "user");
                    ArrayNode content = objectMapper.createArrayNode();
                    ObjectNode textBlock = objectMapper.createObjectNode();
                    textBlock.put("text", text);
                    content.add(textBlock);
                    msg.set("content", content);
                    messages.add(msg);
                }
            }
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.set("messages", messages);

        if (!systemTexts.isEmpty()) {
            ArrayNode systemArr = objectMapper.createArrayNode();
            ObjectNode sysBlock = objectMapper.createObjectNode();
            sysBlock.put("text", String.join("\n\n", systemTexts));
            systemArr.add(sysBlock);
            root.set("system", systemArr);
        }

        ObjectNode inference = objectMapper.createObjectNode();
        if (request.getMaxTokens() != null) {
            inference.put("maxTokens", request.getMaxTokens());
        }
        if (request.getTemperature() != null) {
            inference.put("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            inference.put("topP", request.getTopP());
        }
        if (!inference.isEmpty()) {
            root.set("inferenceConfig", inference);
        }

        return root;
    }

    private ChatCompletionResponse mapConverseResponse(String respBody, String modelName)
            throws IOException {
        JsonNode root = objectMapper.readTree(respBody);
        JsonNode output = root.path("output").path("message");
        String text = "";
        JsonNode content = output.path("content");
        if (content.isArray() && content.size() > 0) {
            text = content.get(0).path("text").asText("");
        }

        String stopReason = root.path("stopReason").asText("end_turn");
        String finishReason = "end_turn".equals(stopReason) ? "stop" : stopReason;

        ChatMessage assistant = new ChatMessage();
        assistant.setRole("assistant");
        assistant.setContent(text);

        Choice choice = new Choice();
        choice.setIndex(0);
        choice.setMessage(assistant);
        choice.setFinishReason(finishReason);

        Usage usage = new Usage();
        JsonNode u = root.path("usage");
        int inTok = u.path("inputTokens").asInt(0);
        int outTok = u.path("outputTokens").asInt(0);
        usage.setPromptTokens(inTok);
        usage.setCompletionTokens(outTok);
        usage.setTotalTokens(u.path("totalTokens").asInt(inTok + outTok));

        ChatCompletionResponse out = new ChatCompletionResponse();
        out.setId(root.path("id").asText("bedrock-converse"));
        out.setObject("chat.completion");
        out.setCreated(System.currentTimeMillis() / 1000);
        out.setModel(modelName);
        out.setChoices(List.of(choice));
        out.setUsage(usage);
        return out;
    }

    private static String resolveRegion(String host, JsonNode creds, String extraParams) {
        String fromCreds = optionalText(creds, "region");
        if (fromCreds != null && !fromCreds.isBlank()) {
            return fromCreds.trim();
        }
        if (extraParams != null && !extraParams.isBlank()) {
            try {
                ObjectMapper om = new ObjectMapper();
                JsonNode n = om.readTree(extraParams);
                JsonNode r = n.get("region");
                if (r != null && !r.asText("").isBlank()) {
                    return r.asText().trim();
                }
            } catch (IOException ignored) {
                // fall through
            }
        }
        if (host != null) {
            Matcher m = RUNTIME_HOST_REGION.matcher(host);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private static String firstNonBlank(JsonNode creds, String missingMessage, String... names) {
        for (String n : names) {
            JsonNode node = creds.get(n);
            if (node != null && !node.isNull()) {
                String v = node.asText("");
                if (!v.isBlank()) {
                    return v;
                }
            }
        }
        throw new IllegalStateException(missingMessage);
    }

    private static String optionalText(JsonNode creds, String name) {
        JsonNode node = creds.get(name);
        if (node == null || node.isNull()) {
            return null;
        }
        String v = node.asText("");
        return v.isEmpty() ? null : v;
    }

    private static String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        return rawQuery;
    }

    private static byte[] deriveSigningKey(String secretKey, String dateStamp, String region, String service) {
        try {
            byte[] kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
            byte[] kDate = hmacRaw(kSecret, dateStamp);
            byte[] kRegion = hmacRaw(kDate, region);
            byte[] kService = hmacRaw(kRegion, service);
            return hmacRaw(kService, "aws4_request");
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Signing key derivation failed", e);
        }
    }

    private static byte[] hmacRaw(byte[] key, String data)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacHex(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return toHex(raw);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC failed", e);
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return toHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String sha256Hex(String data) {
        return sha256Hex(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }
}
