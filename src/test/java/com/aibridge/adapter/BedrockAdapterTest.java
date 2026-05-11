package com.aibridge.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatMessage;
import com.aibridge.exception.ProviderRateLimitException;
import com.aibridge.exception.ProviderUnavailableException;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.LlmProvider;
import com.aibridge.model.enums.ProviderName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.aibridge.service.EncryptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import javax.crypto.Mac;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class BedrockAdapterTest {

    @BeforeAll
    static void allowBedrockRestrictedHeaders() {
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
    }

    @Mock
    EncryptionService encryptionService;

    @Mock
    HttpClient httpClient;

    @Mock
    HttpResponse<String> httpResponse;

    @Test
    void converseRequestBody_containsMessagesArray() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"output":{"message":{"role":"assistant","content":[{"text":"hi"}]}},\
                        "stopReason":"end_turn","usage":{"inputTokens":1,"outputTokens":2,"totalTokens":3}}\
                        """);
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatMessage u = new ChatMessage();
        u.setRole("user");
        u.setContent("Hello");
        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setMessages(List.of(u));

        String endpoint = "https://bedrock-runtime.us-east-1.amazonaws.com/model/foo/converse";
        adapter.complete(req, bedrockConfig(endpoint));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        String body = readRequestBody(cap.getValue());
        JsonNode root = new ObjectMapper().readTree(body);
        assertTrue(root.path("messages").isArray());
    }

    @Test
    void signing_addsAwsAuthorizationHeader() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0,"
                                + "\"totalTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        adapter.complete(minimalRequest(), bedrockConfig(
                "https://bedrock-runtime.us-east-1.amazonaws.com/model/anthropic.claude-v2/converse"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        Optional<String> auth = cap.getValue().headers().firstValue("Authorization");
        assertTrue(auth.isPresent());
        assertTrue(auth.get().startsWith("AWS4-HMAC-SHA256 Credential="));
    }

    @Test
    void http429_throwsProviderRateLimitException() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(429);
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(
                ProviderRateLimitException.class,
                () ->
                        adapter.complete(
                                minimalRequest(),
                                bedrockConfig(
                                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse")));
    }

    @Test
    void http500_throwsProviderUnavailableException() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(500);
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(
                ProviderUnavailableException.class,
                () ->
                        adapter.complete(
                                minimalRequest(),
                                bedrockConfig(
                                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse")));
    }

    @Test
    void emptyBody_returnsEmptyCompletion() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("  ");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        var out =
                adapter.complete(
                        minimalRequest(),
                        bedrockConfig(
                                "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse"));

        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void ioException_throwsProviderUnavailableException() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        doThrow(new IOException("net"))
                .when(httpClient)
                .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(
                ProviderUnavailableException.class,
                () ->
                        adapter.complete(
                                minimalRequest(),
                                bedrockConfig(
                                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse")));
    }

    @Test
    void converseRequestBody_systemMessagesInSystemArray_notInMessages() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"ok\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatMessage sys = new ChatMessage();
        sys.setRole("system");
        sys.setContent("You are helpful.");
        ChatMessage user = new ChatMessage();
        user.setRole("user");
        user.setContent("Hi");
        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setMessages(List.of(sys, user));

        adapter.complete(
                req,
                bedrockConfig(
                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        JsonNode root = new ObjectMapper().readTree(readRequestBody(cap.getValue()));
        assertTrue(root.path("system").isArray());
        assertEquals("You are helpful.", root.path("system").get(0).path("text").asText());
        assertEquals(1, root.path("messages").size());
        assertEquals("user", root.path("messages").get(0).path("role").asText());
    }

    @Test
    void converseRequestBody_assistantRoleInMessages() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatMessage a = new ChatMessage();
        a.setRole("assistant");
        a.setContent("Prev");
        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setMessages(List.of(a));

        adapter.complete(
                req,
                bedrockConfig(
                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        JsonNode root = new ObjectMapper().readTree(readRequestBody(cap.getValue()));
        assertEquals("assistant", root.path("messages").get(0).path("role").asText());
        assertEquals("Prev", root.path("messages").get(0).path("content").get(0).path("text").asText());
    }

    @Test
    void converseRequestBody_skipsNullMessageAndNullRole() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatMessage noRole = new ChatMessage();
        noRole.setContent("skip me");
        ChatMessage user = new ChatMessage();
        user.setRole("user");
        user.setContent("keep");
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(null);
        msgs.add(noRole);
        msgs.add(user);
        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setMessages(msgs);

        adapter.complete(
                req,
                bedrockConfig(
                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        JsonNode root = new ObjectMapper().readTree(readRequestBody(cap.getValue()));
        assertEquals(1, root.path("messages").size());
    }

    @Test
    void converseRequestBody_nullContentUsesEmptyText() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatMessage user = new ChatMessage();
        user.setRole("user");
        user.setContent(null);
        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setMessages(List.of(user));

        adapter.complete(
                req,
                bedrockConfig(
                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        JsonNode root = new ObjectMapper().readTree(readRequestBody(cap.getValue()));
        assertEquals("", root.path("messages").get(0).path("content").get(0).path("text").asText());
    }

    @Test
    void converseRequestBody_unknownRoleMappedToUser() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatMessage tool = new ChatMessage();
        tool.setRole("tool");
        tool.setContent("tool output");
        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setMessages(List.of(tool));

        adapter.complete(
                req,
                bedrockConfig(
                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        JsonNode root = new ObjectMapper().readTree(readRequestBody(cap.getValue()));
        assertEquals("user", root.path("messages").get(0).path("role").asText());
    }

    @Test
    void converseRequestBody_inferenceConfig_maxTokensTemperatureTopP() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatCompletionRequest req = minimalRequest();
        req.setMaxTokens(512);
        req.setTemperature(0.7);
        req.setTopP(0.9);

        adapter.complete(
                req,
                bedrockConfig(
                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        JsonNode root = new ObjectMapper().readTree(readRequestBody(cap.getValue()));
        JsonNode inf = root.path("inferenceConfig");
        assertEquals(512, inf.path("maxTokens").asInt());
        assertEquals(0.7, inf.path("temperature").asDouble());
        assertEquals(0.9, inf.path("topP").asDouble());
    }

    @Test
    void converseRequestBody_nullMessagesList() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setMessages(null);

        adapter.complete(
                req,
                bedrockConfig(
                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        JsonNode root = new ObjectMapper().readTree(readRequestBody(cap.getValue()));
        assertTrue(root.path("messages").isArray());
        assertEquals(0, root.path("messages").size());
        assertTrue(root.path("system").isMissingNode());
    }

    @Test
    void resolveRegion_fromCredentialsJson_takesPrecedenceOverHost() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\","
                                + "\"region\":\"eu-north-1\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        String endpoint =
                "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse";
        adapter.complete(minimalRequest(), bedrockConfig(endpoint));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        String auth = cap.getValue().headers().firstValue("Authorization").orElseThrow();
        assertTrue(
                auth.contains("/eu-north-1/"),
                "Signing scope should use region from credentials JSON, not host");
    }

    @Test
    void resolveRegion_fromExtraParams_whenNotInCredentials() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        LlmConfig cfg =
                bedrockConfig(
                        "https://bedrock-runtime.eu-central-1.amazonaws.com/model/x/converse",
                        "{\"region\":\"ap-southeast-1\"}");
        adapter.complete(minimalRequest(), cfg);

        verify(httpClient).send(any(), any());
    }

    @Test
    void resolveRegion_invalidExtraParamsJson_fallsThroughToHostRegex() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        LlmConfig cfg =
                bedrockConfig(
                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse",
                        "{not valid json");
        adapter.complete(minimalRequest(), cfg);

        verify(httpClient).send(any(), any());
    }

    @Test
    void resolveRegion_notFound_throwsIllegalStateException() {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        LlmConfig cfg = bedrockConfig("https://api.example.com/model/x/converse");

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class, () -> adapter.complete(minimalRequest(), cfg));
        assertTrue(ex.getMessage().contains("region"));
    }

    @Test
    void sessionToken_addsXAmzSecurityTokenHeader() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\","
                                + "\"aws_session_token\":\"sess-token-abc\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        adapter.complete(
                minimalRequest(),
                bedrockConfig(
                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        assertEquals(
                "sess-token-abc",
                cap.getValue().headers().firstValue("x-amz-security-token").orElseThrow());
    }

    @Test
    void optionalText_blankSessionToken_omitsSecurityHeader() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\","
                                + "\"aws_session_token\":\"\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        adapter.complete(
                minimalRequest(),
                bedrockConfig(
                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        assertTrue(cap.getValue().headers().firstValue("x-amz-security-token").isEmpty());
    }

    @Test
    void optionalText_nullSessionTokenJson_omitsSecurityHeader() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\","
                                + "\"aws_session_token\":null}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        adapter.complete(
                minimalRequest(),
                bedrockConfig(
                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        assertTrue(cap.getValue().headers().firstValue("x-amz-security-token").isEmpty());
    }

    @Test
    void normalizeQuery_nonEmptyQueryString_preserved() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        adapter.complete(
                minimalRequest(),
                bedrockConfig(
                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse?trace=1"));

        verify(httpClient).send(any(), any());
    }

    @Test
    void endpoint_emptyPath_usesSlashInCanonicalUri() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        adapter.complete(
                minimalRequest(),
                bedrockConfig("https://bedrock-runtime.us-east-1.amazonaws.com"));

        verify(httpClient).send(any(), any());
    }

    @Test
    void mapConverseResponse_emptyContentArray_yieldsEmptyAssistantText() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[]}},"
                                + "\"stopReason\":\"end_turn\","
                                + "\"usage\":{\"inputTokens\":1,\"outputTokens\":2,\"totalTokens\":3}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        var out =
                adapter.complete(
                        minimalRequest(),
                        bedrockConfig(
                                "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse"));

        assertEquals("", out.getChoices().get(0).getMessage().getContent());
        assertEquals("stop", out.getChoices().get(0).getFinishReason());
    }

    @Test
    void mapConverseResponse_nonEndTurnStopReason_preservedAsFinishReason() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"done\"}]}},"
                                + "\"stopReason\":\"max_tokens\","
                                + "\"usage\":{\"inputTokens\":1,\"outputTokens\":2,\"totalTokens\":3}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        var out =
                adapter.complete(
                        minimalRequest(),
                        bedrockConfig(
                                "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse"));

        assertEquals("max_tokens", out.getChoices().get(0).getFinishReason());
    }

    @Test
    void mapConverseResponse_missingTotalTokens_fallsBackToInputPlusOutput() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\","
                                + "\"usage\":{\"inputTokens\":4,\"outputTokens\":5}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        var out =
                adapter.complete(
                        minimalRequest(),
                        bedrockConfig(
                                "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse"));

        assertEquals(9, out.getUsage().getTotalTokens());
    }

    @Test
    void interruptedException_restoresInterruptFlag() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        doThrow(new InterruptedException("interrupted"))
                .when(httpClient)
                .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        Thread.interrupted();

        assertThrows(
                ProviderUnavailableException.class,
                () ->
                        adapter.complete(
                                minimalRequest(),
                                bedrockConfig(
                                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse")));

        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }

    @Test
    void invalidCredentialsJson_throwsIllegalStateException() {
        when(encryptionService.decrypt(any())).thenReturn("not-json{");
        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                adapter.complete(
                                        minimalRequest(),
                                        bedrockConfig(
                                                "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse")));
        assertTrue(ex.getMessage().contains("Invalid credentials JSON"));
    }

    @Test
    void missingAwsAccessKey_throwsIllegalStateException() {
        when(encryptionService.decrypt(any()))
                .thenReturn("{\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                adapter.complete(
                                        minimalRequest(),
                                        bedrockConfig(
                                                "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse")));
        assertTrue(ex.getMessage().contains("access key"));
    }

    @Test
    void missingAwsSecretKey_throwsIllegalStateException() {
        when(encryptionService.decrypt(any()))
                .thenReturn("{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\"}");
        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                adapter.complete(
                                        minimalRequest(),
                                        bedrockConfig(
                                                "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse")));
        assertTrue(ex.getMessage().contains("secret key"));
    }

    @Test
    void firstNonBlank_skipsBlankValues_usesAlternateKeyNames() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"\","
                                + "\"aws_access_key_id\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_access_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        adapter.complete(
                minimalRequest(),
                bedrockConfig(
                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse"));

        verify(httpClient).send(any(), any());
    }

    @Test
    void nullResponseBody_returnsEmptyCompletion() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(null);
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        var out =
                adapter.complete(
                        minimalRequest(),
                        bedrockConfig(
                                "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse"));

        assertEquals("", out.getChoices().get(0).getMessage().getContent());
        assertEquals("chatcmpl-empty", out.getId());
    }

    @Test
    void getProviderName_returnsBedrock() {
        BedrockAdapter adapter = createAdapter(httpClient);
        assertEquals(ProviderName.BEDROCK, adapter.getProviderName());
    }

    @Test
    void complete_writeValueAsStringIOException_throwsIllegalStateException() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper =
                new ObjectMapper() {
                    @Override
                    public String writeValueAsString(Object value) throws JsonProcessingException {
                        throw new JsonProcessingException("serialization failed") {};
                    }
                };

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                adapter.complete(
                                        minimalRequest(),
                                        bedrockConfig(
                                                "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse")));
        assertTrue(ex.getMessage().contains("Failed to serialize Bedrock request"));
    }

    @Test
    void mapConverseResponse_contentNotArray_yieldsEmptyAssistantText() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":{\"text\":\"nope\"}}},"
                                + "\"stopReason\":\"end_turn\","
                                + "\"usage\":{\"inputTokens\":1,\"outputTokens\":2,\"totalTokens\":3}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        var out =
                adapter.complete(
                        minimalRequest(),
                        bedrockConfig(
                                "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse"));

        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void normalizeQuery_emptyRawQueryString_afterQuestionMark() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        adapter.complete(
                minimalRequest(),
                bedrockConfig(
                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse?"));

        verify(httpClient).send(any(), any());
    }

    @Test
    void firstNonBlank_skipsNullJsonNode_usesAlternateKeyName() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":null,"
                                + "\"aws_access_key_id\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        adapter.complete(
                minimalRequest(),
                bedrockConfig(
                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse"));

        verify(httpClient).send(any(), any());
    }

    @Test
    void resolveRegion_extraParamsBlankRegion_fallsThroughToHostRegex() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        adapter.complete(
                minimalRequest(),
                bedrockConfig(
                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse",
                        "{\"region\":\"\"}"));

        verify(httpClient).send(any(), any());
    }

    @Test
    void resolveRegion_extraParamsWithoutRegionKey_fallsThroughToHostRegex() throws Exception {
        when(encryptionService.decrypt(any()))
                .thenReturn(
                        "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"x\"}]}},"
                                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":0,\"outputTokens\":0}}");
        stubSend(httpClient, httpResponse);

        BedrockAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        adapter.complete(
                minimalRequest(),
                bedrockConfig(
                        "https://bedrock-runtime.us-east-1.amazonaws.com/model/x/converse",
                        "{\"foo\":\"bar\"}"));

        verify(httpClient).send(any(), any());
    }

    @Test
    void sha256Hex_stringOverload_invokedViaReflection() throws Exception {
        Method m = BedrockAdapter.class.getDeclaredMethod("sha256Hex", String.class);
        m.setAccessible(true);
        String hex = (String) m.invoke(null, "payload");
        assertEquals(64, hex.length());
    }

    @Test
    void resolveRegion_reflection_nullHostWithoutRegion_returnsNull() throws Exception {
        Method m =
                BedrockAdapter.class.getDeclaredMethod(
                        "resolveRegion", String.class, JsonNode.class, String.class);
        m.setAccessible(true);
        JsonNode creds =
                new ObjectMapper()
                        .readTree(
                                "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                        + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        assertNull(m.invoke(null, null, creds, null));
    }

    @Test
    void resolveRegion_reflection_whitespaceOnlyExtraParams_skipsJsonBlock() throws Exception {
        Method m =
                BedrockAdapter.class.getDeclaredMethod(
                        "resolveRegion", String.class, JsonNode.class, String.class);
        m.setAccessible(true);
        JsonNode creds =
                new ObjectMapper()
                        .readTree(
                                "{\"aws_access_key\":\"AKIAIOSFODNN7EXAMPLE\","
                                        + "\"aws_secret_key\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}");
        String region =
                (String)
                        m.invoke(
                                null,
                                "bedrock-runtime.us-east-1.amazonaws.com",
                                creds,
                                "  \t\n  ");
        assertEquals("us-east-1", region);
    }

    @Test
    void deriveSigningKey_reflection_noSuchAlgorithm_wrapsIllegalState() throws Exception {
        Method m =
                BedrockAdapter.class.getDeclaredMethod(
                        "deriveSigningKey", String.class, String.class, String.class, String.class);
        m.setAccessible(true);
        try (MockedStatic<Mac> mac = mockStatic(Mac.class)) {
            mac.when(() -> Mac.getInstance("HmacSHA256"))
                    .thenThrow(new NoSuchAlgorithmException("none"));
            InvocationTargetException ex =
                    assertThrows(
                            InvocationTargetException.class,
                            () ->
                                    m.invoke(
                                            null,
                                            "secret",
                                            "20240101",
                                            "us-east-1",
                                            "bedrock"));
            assertTrue(ex.getCause() instanceof IllegalStateException);
            assertTrue(ex.getCause().getMessage().contains("Signing key derivation failed"));
        }
    }

    @Test
    void hmacHex_reflection_noSuchAlgorithm_wrapsIllegalState() throws Exception {
        Method m =
                BedrockAdapter.class.getDeclaredMethod(
                        "hmacHex", byte[].class, String.class);
        m.setAccessible(true);
        try (MockedStatic<Mac> mac = mockStatic(Mac.class)) {
            mac.when(() -> Mac.getInstance("HmacSHA256"))
                    .thenThrow(new NoSuchAlgorithmException("none"));
            InvocationTargetException ex =
                    assertThrows(
                            InvocationTargetException.class,
                            () -> m.invoke(null, new byte[16], "data"));
            assertTrue(ex.getCause() instanceof IllegalStateException);
            assertTrue(ex.getCause().getMessage().contains("HMAC failed"));
        }
    }

    @Test
    void sha256Hex_bytes_reflection_noSuchAlgorithm_wrapsIllegalState() throws Exception {
        Method m = BedrockAdapter.class.getDeclaredMethod("sha256Hex", byte[].class);
        m.setAccessible(true);
        try (MockedStatic<MessageDigest> md = mockStatic(MessageDigest.class)) {
            md.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException("none"));
            InvocationTargetException ex =
                    assertThrows(
                            InvocationTargetException.class,
                            () -> m.invoke(null, (Object) "x".getBytes(StandardCharsets.UTF_8)));
            assertTrue(ex.getCause() instanceof IllegalStateException);
            assertTrue(ex.getCause().getMessage().contains("SHA-256 not available"));
        }
    }

    @SuppressWarnings("unchecked")
    private static void stubSend(HttpClient client, HttpResponse<String> response) {
        try {
            doReturn(response)
                    .when(client)
                    .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static BedrockAdapter createAdapter(HttpClient client) {
        HttpClient.Builder builder = mock(HttpClient.Builder.class);
        when(builder.connectTimeout(any())).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        try (var hs = mockStatic(HttpClient.class)) {
            hs.when(HttpClient::newBuilder).thenReturn(builder);
            return new BedrockAdapter();
        }
    }

    private static String readRequestBody(HttpRequest request) throws Exception {
        HttpRequest.BodyPublisher pub = request.bodyPublisher().orElseThrow();
        CompletableFuture<byte[]> done = new CompletableFuture<>();
        pub.subscribe(
                new Flow.Subscriber<>() {
                    private final java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    private Flow.Subscription subscription;

                    @Override
                    public void onSubscribe(Flow.Subscription s) {
                        subscription = s;
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ByteBuffer buffer) {
                        byte[] chunk = new byte[buffer.remaining()];
                        buffer.get(chunk);
                        baos.write(chunk, 0, chunk.length);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        done.completeExceptionally(throwable);
                    }

                    @Override
                    public void onComplete() {
                        done.complete(baos.toByteArray());
                    }
                });
        byte[] bytes = done.get(5, TimeUnit.SECONDS);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static ChatCompletionRequest minimalRequest() {
        ChatMessage u = new ChatMessage();
        u.setRole("user");
        u.setContent("Hi");
        ChatCompletionRequest r = new ChatCompletionRequest();
        r.setMessages(List.of(u));
        return r;
    }

    private static LlmConfig bedrockConfig(String endpoint) {
        LlmProvider p = new LlmProvider();
        p.setName(ProviderName.BEDROCK);
        LlmConfig c = new LlmConfig();
        c.setProvider(p);
        c.setEndpointUrl(endpoint);
        c.setModelName("anthropic.claude-v2");
        c.setCredentialsEncrypted("enc");
        return c;
    }

    private static LlmConfig bedrockConfig(String endpoint, String extraParams) {
        LlmConfig c = bedrockConfig(endpoint);
        c.setExtraParams(extraParams);
        return c;
    }
}
