package com.aibridge.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibridge.adapter.auth.AuthTokenManager;
import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatMessage;
import com.aibridge.exception.ProviderRateLimitException;
import com.aibridge.exception.ProviderUnavailableException;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.LlmProvider;
import com.aibridge.model.enums.ProviderName;
import com.aibridge.service.EncryptionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class WatsonXAdapterTest {

    @Mock
    EncryptionService encryptionService;

    @Mock
    AuthTokenManager authTokenManager;

    @Mock
    HttpClient httpClient;

    @Mock
    HttpResponse<String> httpResponse;

    @Test
    void requestBody_includesProjectIdAndModelId() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"id":"wx1","choices":[{"message":{"role":"assistant","content":"yo"},\
                        "finish_reason":"stop"}],\
                        "usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}\
                        """);
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        adapter.complete(minimalRequest(), watsonConfig());

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        String body = readRequestBody(cap.getValue());
        JsonNode root = new ObjectMapper().readTree(body);
        assertEquals("proj-xyz", root.path("project_id").asText());
        assertEquals("ibm/granite", root.path("model_id").asText());
    }

    @Test
    void responseMapping_mapsAssistantMessage() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"id":"wx1","model_id":"ibm/granite","choices":[\
                        {"message":{"role":"assistant","content":"mapped"},"finish_reason":"length"}],\
                        "usage":{"prompt_tokens":5,"completion_tokens":6,"total_tokens":11}}\
                        """);
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        var out = adapter.complete(minimalRequest(), watsonConfig());

        assertEquals("mapped", out.getChoices().get(0).getMessage().getContent());
        assertEquals("length", out.getChoices().get(0).getFinishReason());
    }

    @Test
    void http429_throwsProviderRateLimitException() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(429);
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(ProviderRateLimitException.class, () -> adapter.complete(minimalRequest(), watsonConfig()));
    }

    @Test
    void http502_throwsProviderUnavailableException() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(502);
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(ProviderUnavailableException.class, () -> adapter.complete(minimalRequest(), watsonConfig()));
    }

    @Test
    void emptyBody_returnsEmptyCompletion() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("");
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        var out = adapter.complete(minimalRequest(), watsonConfig());

        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void ioException_throwsProviderUnavailableException() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        doThrow(new IOException("net"))
                .when(httpClient)
                .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(ProviderUnavailableException.class, () -> adapter.complete(minimalRequest(), watsonConfig()));
    }

    @Test
    void getProviderName_returnsWatsonx() {
        WatsonXAdapter adapter = createAdapter(httpClient);
        assertEquals(ProviderName.WATSONX, adapter.getProviderName());
    }

    @Test
    void parseProjectId_nullExtraParams_throwsIllegalStateException() {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        LlmConfig cfg = watsonConfigWithExtraParams(null);
        assertThrows(IllegalStateException.class, () -> adapter.complete(minimalRequest(), cfg));
    }

    @Test
    void parseProjectId_blankExtraParams_throwsIllegalStateException() {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        LlmConfig cfg = watsonConfigWithExtraParams("   ");
        assertThrows(IllegalStateException.class, () -> adapter.complete(minimalRequest(), cfg));
    }

    @Test
    void parseProjectId_invalidJsonExtraParams_throwsIllegalStateException() {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        LlmConfig cfg = watsonConfigWithExtraParams("{not-json");
        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> adapter.complete(minimalRequest(), cfg));
        assertTrue(ex.getMessage().contains("extra_params"));
    }

    @Test
    void parseProjectId_missingProjectId_throwsIllegalStateException() {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        LlmConfig cfg = watsonConfigWithExtraParams("{}");
        assertThrows(IllegalStateException.class, () -> adapter.complete(minimalRequest(), cfg));
    }

    @Test
    void parseProjectId_nullJsonProjectId_throwsIllegalStateException() {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        LlmConfig cfg = watsonConfigWithExtraParams("{\"project_id\":null}");
        assertThrows(IllegalStateException.class, () -> adapter.complete(minimalRequest(), cfg));
    }

    @Test
    void parseProjectId_blankJsonProjectId_throwsIllegalStateException() {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        LlmConfig cfg = watsonConfigWithExtraParams("{\"project_id\":\"\"}");
        assertThrows(IllegalStateException.class, () -> adapter.complete(minimalRequest(), cfg));
    }

    @Test
    void complete_nullMessages_skipsMessageLoop() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"choices":[{"message":{"role":"assistant","content":"ok"}}],\
                        "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}\
                        """);
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setMessages(null);
        adapter.complete(req, watsonConfig());

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        String body = readRequestBody(cap.getValue());
        JsonNode root = new ObjectMapper().readTree(body);
        assertTrue(root.path("messages").isArray());
        assertEquals(0, root.path("messages").size());
    }

    @Test
    void complete_skipsNullMessageNullRoleAndSerializesNullContentAsEmpty() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"choices\":[],\"usage\":{}}");
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        ChatMessage valid = new ChatMessage();
        valid.setRole("user");
        valid.setContent("keep");
        ChatMessage nullRole = new ChatMessage();
        nullRole.setRole(null);
        nullRole.setContent("skip");
        ChatMessage nullContent = new ChatMessage();
        nullContent.setRole("assistant");
        nullContent.setContent(null);

        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(null);
        msgs.add(nullRole);
        msgs.add(nullContent);
        msgs.add(valid);

        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setMessages(msgs);
        adapter.complete(req, watsonConfig());

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        String body = readRequestBody(cap.getValue());
        JsonNode root = new ObjectMapper().readTree(body);
        assertEquals(2, root.path("messages").size());
        assertEquals("", root.path("messages").get(0).path("content").asText());
        assertEquals("keep", root.path("messages").get(1).path("content").asText());
    }

    @Test
    void complete_includesMaxTokensAndTemperatureInParameters() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"choices\":[]}");
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        ChatCompletionRequest req = minimalRequest();
        req.setMaxTokens(256);
        req.setTemperature(0.7);
        adapter.complete(req, watsonConfig());

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        String body = readRequestBody(cap.getValue());
        JsonNode root = new ObjectMapper().readTree(body);
        assertEquals(256, root.path("parameters").path("max_new_tokens").asInt());
        assertEquals(0.7, root.path("parameters").path("temperature").asDouble());
    }

    @Test
    void mapWatsonXResponse_missingChoicesKey_returnsEmpty() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{}");
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        var out = adapter.complete(minimalRequest(), watsonConfig());
        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void mapWatsonXResponse_choicesNotArray_returnsEmpty() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"choices\":{}}");
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        var out = adapter.complete(minimalRequest(), watsonConfig());
        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void mapWatsonXResponse_nullChoices_returnsEmpty() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"choices\":null}");
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        var out = adapter.complete(minimalRequest(), watsonConfig());
        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void mapWatsonXResponse_emptyChoices_returnsEmpty() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"choices\":[]}");
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        var out = adapter.complete(minimalRequest(), watsonConfig());
        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void mapWatsonXResponse_missingMessage_usesDefaultAssistantRoleAndContent() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"choices":[{"finish_reason":"stop"}],\
                        "usage":null}\
                        """);
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        var out = adapter.complete(minimalRequest(), watsonConfig());
        assertEquals("assistant", out.getChoices().get(0).getMessage().getRole());
        assertEquals("", out.getChoices().get(0).getMessage().getContent());
        assertEquals(0, out.getUsage().getTotalTokens());
    }

    @Test
    void mapWatsonXResponse_usageAlternativeTokenFields() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"choices":[{"message":{"content":"x"}}],\
                        "usage":{"input_tokens":9,"generated_tokens":3}}\
                        """);
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        var out = adapter.complete(minimalRequest(), watsonConfig());
        assertEquals(9, out.getUsage().getPromptTokens());
        assertEquals(3, out.getUsage().getCompletionTokens());
        assertEquals(12, out.getUsage().getTotalTokens());
    }

    @Test
    void mapWatsonXResponse_messageWithoutContentField_treatsContentAsNull() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn("{\"choices\":[{\"message\":{\"role\":\"assistant\"}}],\"usage\":{}}");
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        var out = adapter.complete(minimalRequest(), watsonConfig());
        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void mapWatsonXResponse_messageContentNull_returnsEmptyString() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null}}]}");
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        var out = adapter.complete(minimalRequest(), watsonConfig());
        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void mapWatsonXResponse_messageContentTextual_returnsText() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"plain\"}}],\"usage\":{}}");
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        var out = adapter.complete(minimalRequest(), watsonConfig());
        assertEquals("plain", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void mapWatsonXResponse_messageContentArrayWithTextParts_concatenates() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"choices":[{"message":{"content":[{"text":"a"},{"text":"b"}]}}],"usage":{}}\
                        """);
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        var out = adapter.complete(minimalRequest(), watsonConfig());
        assertEquals("ab", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void mapWatsonXResponse_messageContentArray_skipsPartsWithoutTextOrTextual() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"choices":[{"message":{"content":[{"text":"a"},{},42]}}],"usage":{}}\
                        """);
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        var out = adapter.complete(minimalRequest(), watsonConfig());
        assertEquals("a", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void mapWatsonXResponse_messageContentArrayWithTextualNodes_concatenates() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn("{\"choices\":[{\"message\":{\"content\":[\"x\",\"y\"]}}],\"usage\":{}}");
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        var out = adapter.complete(minimalRequest(), watsonConfig());
        assertEquals("xy", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void mapWatsonXResponse_messageContentNonTextNonArray_usesAsTextFallback() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn("{\"choices\":[{\"message\":{\"content\":true}}],\"usage\":{}}");
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        var out = adapter.complete(minimalRequest(), watsonConfig());
        assertEquals("true", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void interruptedException_setsInterruptFlag() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        doThrow(new InterruptedException("intr"))
                .when(httpClient)
                .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        try {
            assertThrows(
                    ProviderUnavailableException.class, () -> adapter.complete(minimalRequest(), watsonConfig()));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void writeRequestJsonIOException_throwsIllegalStateException() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");

        ObjectMapper failingMapper =
                new ObjectMapper() {
                    @Override
                    public String writeValueAsString(Object value) throws JsonProcessingException {
                        throw new JsonProcessingException("fail") {};
                    }
                };

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = failingMapper;

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> adapter.complete(minimalRequest(), watsonConfig()));
        assertTrue(ex.getMessage().contains("serialize"));
    }

    @Test
    void nullResponseBody_returnsEmptyCompletion() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        when(authTokenManager.getToken(any())).thenReturn("tok");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(null);
        stubSend(httpClient, httpResponse);

        WatsonXAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.authTokenManager = authTokenManager;
        adapter.objectMapper = new ObjectMapper();

        var out = adapter.complete(minimalRequest(), watsonConfig());
        assertEquals("", out.getChoices().get(0).getMessage().getContent());
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

    private static WatsonXAdapter createAdapter(HttpClient client) {
        HttpClient.Builder builder = mock(HttpClient.Builder.class);
        when(builder.connectTimeout(any())).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        try (var hs = mockStatic(HttpClient.class)) {
            hs.when(HttpClient::newBuilder).thenReturn(builder);
            return new WatsonXAdapter();
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

    private static LlmConfig watsonConfig() {
        LlmProvider p = new LlmProvider();
        p.setName(ProviderName.WATSONX);
        p.setId(UUID.randomUUID());
        LlmConfig c = new LlmConfig();
        c.setId(UUID.randomUUID());
        c.setProvider(p);
        c.setEndpointUrl("https://us-south.ml.cloud.ibm.com/ml/v1/text/chat?version=2023-05-29");
        c.setModelName("ibm/granite");
        c.setCredentialsEncrypted("enc");
        c.setExtraParams("{\"project_id\":\"proj-xyz\"}");
        return c;
    }

    private static LlmConfig watsonConfigWithExtraParams(String extraParams) {
        LlmConfig c = watsonConfig();
        c.setExtraParams(extraParams);
        return c;
    }
}
