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

import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatCompletionResponse;
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
import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
class ClaudeAdapterTest {

    @Mock
    EncryptionService encryptionService;

    @Mock
    HttpClient httpClient;

    @Mock
    HttpResponse<String> httpResponse;

    @Test
    void requestBody_extractsSystemMessageIntoTopLevelField() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"id":"1","model":"claude","stop_reason":"end_turn","content":[\
                        {"type":"text","text":"ok"}],\
                        "usage":{"input_tokens":1,"output_tokens":2}}\
                        """);
        stubSend(httpClient, httpResponse);

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatMessage sys = new ChatMessage();
        sys.setRole("system");
        sys.setContent("You are helpful");
        ChatMessage user = new ChatMessage();
        user.setRole("user");
        user.setContent("Hi");
        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setMessages(List.of(sys, user));

        adapter.complete(req, minimalConfig("https://api.anthropic.com/v1/messages"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        String body = readRequestBody(cap.getValue());
        JsonNode root = new ObjectMapper().readTree(body);
        assertEquals("You are helpful", root.path("system").asText());
        assertTrue(root.path("messages").isArray());
    }

    @Test
    void responseMapping_endTurnMapsToStopFinishReason() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"id":"1","model":"claude-3","stop_reason":"end_turn","content":[\
                        {"type":"text","text":"done"}],\
                        "usage":{"input_tokens":3,"output_tokens":4}}\
                        """);
        stubSend(httpClient, httpResponse);

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatCompletionResponse out =
                adapter.complete(minimalRequest(), minimalConfig("https://api.anthropic.com/v1/messages"));

        assertEquals("stop", out.getChoices().get(0).getFinishReason());
    }

    @Test
    void http429_throwsProviderRateLimitException() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(429);
        stubSend(httpClient, httpResponse);

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(
                ProviderRateLimitException.class,
                () -> adapter.complete(minimalRequest(), minimalConfig("https://api.anthropic.com/v1/messages")));
    }

    @Test
    void http500_throwsProviderUnavailableException() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(503);
        stubSend(httpClient, httpResponse);

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(
                ProviderUnavailableException.class,
                () -> adapter.complete(minimalRequest(), minimalConfig("https://api.anthropic.com/v1/messages")));
    }

    @Test
    void emptyBody_returnsEmptyCompletion() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("");
        stubSend(httpClient, httpResponse);

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatCompletionResponse out =
                adapter.complete(minimalRequest(), minimalConfig("https://api.anthropic.com/v1/messages"));

        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void ioException_throwsProviderUnavailableException() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        doThrow(new IOException("net"))
                .when(httpClient)
                .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(
                ProviderUnavailableException.class,
                () -> adapter.complete(minimalRequest(), minimalConfig("https://api.anthropic.com/v1/messages")));
    }

    @Test
    void getProviderName_returnsClaude() {
        ClaudeAdapter adapter = createAdapter(httpClient);
        assertEquals(ProviderName.CLAUDE, adapter.getProviderName());
    }

    @Test
    void invalidCredentialsJson_throwsIllegalStateException() {
        when(encryptionService.decrypt(any())).thenReturn("{not-json");
        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> adapter.complete(minimalRequest(), minimalConfig("https://api.anthropic.com/v1/messages")));
        assertTrue(ex.getMessage().contains("credentials"));
    }

    @Test
    void missingApiKey_throwsIllegalStateException() {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(
                IllegalStateException.class,
                () -> adapter.complete(minimalRequest(), minimalConfig("https://api.anthropic.com/v1/messages")));
    }

    @Test
    void blankApiKey_throwsIllegalStateException() {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"  \"}");
        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(
                IllegalStateException.class,
                () -> adapter.complete(minimalRequest(), minimalConfig("https://api.anthropic.com/v1/messages")));
    }

    @Test
    void nullMessages_addsDefaultEmptyUserMessage() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"id":"1","model":"claude","stop_reason":"stop","content":[\
                        {"type":"text","text":"ok"}],\
                        "usage":{"input_tokens":0,"output_tokens":0}}\
                        """);
        stubSend(httpClient, httpResponse);

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setMessages(null);
        adapter.complete(req, minimalConfig("https://api.anthropic.com/v1/messages"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        JsonNode root = new ObjectMapper().readTree(readRequestBody(cap.getValue()));
        assertEquals(1, root.path("messages").size());
        assertEquals("user", root.path("messages").get(0).path("role").asText());
        assertEquals("", root.path("messages").get(0).path("content").get(0).path("text").asText());
    }

    @Test
    void nullChatMessageAndNullRole_skipped_nullContentSerializedAsEmpty() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"id":"1","content":[{"type":"text","text":"x"}],\
                        "usage":{"input_tokens":0,"output_tokens":0}}\
                        """);
        stubSend(httpClient, httpResponse);

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatMessage ok = new ChatMessage();
        ok.setRole("user");
        ok.setContent("hi");
        ChatMessage nullRole = new ChatMessage();
        nullRole.setRole(null);
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(null);
        msgs.add(nullRole);
        msgs.add(ok);

        ChatMessage nullContent = new ChatMessage();
        nullContent.setRole("assistant");
        nullContent.setContent(null);
        msgs.add(nullContent);

        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setMessages(msgs);
        adapter.complete(req, minimalConfig("https://api.anthropic.com/v1/messages"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        JsonNode root = new ObjectMapper().readTree(readRequestBody(cap.getValue()));
        assertEquals(2, root.path("messages").size());
        assertEquals("", root.path("messages").get(1).path("content").get(0).path("text").asText());
    }

    @Test
    void onlySystemMessage_addsEmptyUserMessage() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"id":"1","content":[{"type":"text","text":"x"}],\
                        "usage":{"input_tokens":0,"output_tokens":0}}\
                        """);
        stubSend(httpClient, httpResponse);

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatMessage sys = new ChatMessage();
        sys.setRole("system");
        sys.setContent("sys only");
        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setMessages(List.of(sys));
        adapter.complete(req, minimalConfig("https://api.anthropic.com/v1/messages"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        JsonNode root = new ObjectMapper().readTree(readRequestBody(cap.getValue()));
        assertEquals("sys only", root.path("system").asText());
        assertEquals(1, root.path("messages").size());
        assertEquals("user", root.path("messages").get(0).path("role").asText());
    }

    @Test
    void maxTokens_null_defaultsTo1024() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"id":"1","content":[{"type":"text","text":"x"}],\
                        "usage":{"input_tokens":0,"output_tokens":0}}\
                        """);
        stubSend(httpClient, httpResponse);

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatCompletionRequest req = minimalRequest();
        req.setMaxTokens(null);
        adapter.complete(req, minimalConfig("https://api.anthropic.com/v1/messages"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        assertEquals(1024, new ObjectMapper().readTree(readRequestBody(cap.getValue())).path("max_tokens").asInt());
    }

    @Test
    void maxTokens_zero_defaultsTo1024() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"id":"1","content":[{"type":"text","text":"x"}],\
                        "usage":{"input_tokens":0,"output_tokens":0}}\
                        """);
        stubSend(httpClient, httpResponse);

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatCompletionRequest req = minimalRequest();
        req.setMaxTokens(0);
        adapter.complete(req, minimalConfig("https://api.anthropic.com/v1/messages"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        assertEquals(1024, new ObjectMapper().readTree(readRequestBody(cap.getValue())).path("max_tokens").asInt());
    }

    @Test
    void maxTokens_positive_passedThrough() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"id":"1","content":[{"type":"text","text":"x"}],\
                        "usage":{"input_tokens":0,"output_tokens":0}}\
                        """);
        stubSend(httpClient, httpResponse);

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatCompletionRequest req = minimalRequest();
        req.setMaxTokens(512);
        adapter.complete(req, minimalConfig("https://api.anthropic.com/v1/messages"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        assertEquals(512, new ObjectMapper().readTree(readRequestBody(cap.getValue())).path("max_tokens").asInt());
    }

    @Test
    void temperature_set_includesInRequestBody() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"id":"1","content":[{"type":"text","text":"x"}],\
                        "usage":{"input_tokens":0,"output_tokens":0}}\
                        """);
        stubSend(httpClient, httpResponse);

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatCompletionRequest req = minimalRequest();
        req.setTemperature(0.33);
        adapter.complete(req, minimalConfig("https://api.anthropic.com/v1/messages"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        assertEquals(0.33, new ObjectMapper().readTree(readRequestBody(cap.getValue())).path("temperature").asDouble());
    }

    @Test
    void mapClaudeResponse_emptyContentArray_mapsEmptyAssistantText() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn("{\"id\":\"1\",\"model\":\"m\",\"stop_reason\":\"stop\",\"content\":[],\"usage\":{}}");
        stubSend(httpClient, httpResponse);

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatCompletionResponse out =
                adapter.complete(minimalRequest(), minimalConfig("https://api.anthropic.com/v1/messages"));
        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void mapClaudeResponse_nonTextFirstContentType_mapsEmptyText() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"id":"1","model":"m","stop_reason":"stop","content":[\
                        {"type":"tool_use","id":"t","name":"n","input":{}}],\
                        "usage":{"input_tokens":1,"output_tokens":2}}\
                        """);
        stubSend(httpClient, httpResponse);

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatCompletionResponse out =
                adapter.complete(minimalRequest(), minimalConfig("https://api.anthropic.com/v1/messages"));
        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void mapClaudeResponse_minimalJson_usesDefaults() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{}");
        stubSend(httpClient, httpResponse);

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatCompletionResponse out =
                adapter.complete(minimalRequest(), minimalConfig("https://api.anthropic.com/v1/messages"));
        assertEquals("claude-msg", out.getId());
        assertEquals("", out.getChoices().get(0).getMessage().getContent());
        assertEquals("stop", out.getChoices().get(0).getFinishReason());
    }

    @Test
    void mapFinishReason_nullAndEmptyMapToStop() throws Exception {
        Method m = ClaudeAdapter.class.getDeclaredMethod("mapFinishReason", String.class);
        m.setAccessible(true);
        assertEquals("stop", m.invoke(null, (Object) null));
        assertEquals("stop", m.invoke(null, ""));
    }

    @Test
    void mapFinishReason_endTurn_mapsToStop() throws Exception {
        Method m = ClaudeAdapter.class.getDeclaredMethod("mapFinishReason", String.class);
        m.setAccessible(true);
        assertEquals("stop", m.invoke(null, "end_turn"));
    }

    @Test
    void mapFinishReason_otherValue_preserved() throws Exception {
        Method m = ClaudeAdapter.class.getDeclaredMethod("mapFinishReason", String.class);
        m.setAccessible(true);
        assertEquals("max_tokens", m.invoke(null, "max_tokens"));
    }

    @Test
    void interruptedException_setsInterruptFlag() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        doThrow(new InterruptedException("intr"))
                .when(httpClient)
                .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        try {
            assertThrows(
                    ProviderUnavailableException.class,
                    () -> adapter.complete(minimalRequest(), minimalConfig("https://api.anthropic.com/v1/messages")));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void unknownMessageRole_skippedNotAddedToClaudeMessages() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"id":"1","content":[{"type":"text","text":"x"}],\
                        "usage":{"input_tokens":0,"output_tokens":0}}\
                        """);
        stubSend(httpClient, httpResponse);

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatMessage tool = new ChatMessage();
        tool.setRole("tool");
        tool.setContent("ignored");
        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setMessages(List.of(tool));
        adapter.complete(req, minimalConfig("https://api.anthropic.com/v1/messages"));

        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(cap.capture(), any());
        JsonNode root = new ObjectMapper().readTree(readRequestBody(cap.getValue()));
        assertEquals(1, root.path("messages").size());
        assertEquals("user", root.path("messages").get(0).path("role").asText());
    }

    @Test
    void writeRequestJsonIOException_throwsIllegalStateException() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");

        ObjectMapper failingMapper =
                new ObjectMapper() {
                    @Override
                    public String writeValueAsString(Object value) throws JsonProcessingException {
                        throw new JsonProcessingException("fail") {};
                    }
                };

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = failingMapper;

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> adapter.complete(minimalRequest(), minimalConfig("https://api.anthropic.com/v1/messages")));
        assertTrue(ex.getMessage().contains("serialize"));
    }

    @Test
    void nullResponseBody_returnsEmptyCompletion() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(null);
        stubSend(httpClient, httpResponse);

        ClaudeAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatCompletionResponse out =
                adapter.complete(minimalRequest(), minimalConfig("https://api.anthropic.com/v1/messages"));
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

    private static ClaudeAdapter createAdapter(HttpClient client) {
        HttpClient.Builder builder = mock(HttpClient.Builder.class);
        when(builder.connectTimeout(any())).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        try (var hs = mockStatic(HttpClient.class)) {
            hs.when(HttpClient::newBuilder).thenReturn(builder);
            return new ClaudeAdapter();
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
        ChatMessage user = new ChatMessage();
        user.setRole("user");
        user.setContent("Hi");
        ChatCompletionRequest r = new ChatCompletionRequest();
        r.setMessages(List.of(user));
        return r;
    }

    private static LlmConfig minimalConfig(String url) {
        LlmProvider p = new LlmProvider();
        p.setName(ProviderName.CLAUDE);
        LlmConfig c = new LlmConfig();
        c.setProvider(p);
        c.setEndpointUrl(url);
        c.setModelName("claude-3");
        c.setCredentialsEncrypted("enc");
        return c;
    }
}
