package com.aibridge.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatCompletionResponse;
import com.aibridge.dto.openai.ChatMessage;
import com.aibridge.dto.openai.Choice;
import com.aibridge.exception.ProviderRateLimitException;
import com.aibridge.exception.ProviderUnavailableException;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.LlmProvider;
import com.aibridge.model.enums.ProviderName;
import com.aibridge.service.EncryptionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class OpenAiAdapterTest {

    @Mock
    EncryptionService encryptionService;

    @Mock
    HttpClient httpClient;

    @Mock
    HttpResponse<String> httpResponse;

    @Test
    void successfulResponse_parsesChatCompletionBody() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"sk-test\"}");

        String json =
                """
                {"id":"cmpl-1","object":"chat.completion","created":1,"model":"gpt-4",\
                "choices":[{"index":0,"message":{"role":"assistant","content":"hello"},\
                "finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":2,\
                "total_tokens":3}}\
                """;

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);
        stubSend(httpClient, httpResponse);

        OpenAiAdapter adapter = createAdapterWithClient(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatCompletionResponse out =
                adapter.complete(minimalRequest(), minimalConfig("https://api.example.com"));

        assertEquals("hello", out.getChoices().get(0).getMessage().getContent());
        assertEquals(3, out.getUsage().getTotalTokens());
    }

    @Test
    void http429_throwsProviderRateLimitException() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(429);
        stubSend(httpClient, httpResponse);

        OpenAiAdapter adapter = createAdapterWithClient(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(
                ProviderRateLimitException.class,
                () -> adapter.complete(minimalRequest(), minimalConfig("https://api.example.com")));
    }

    @Test
    void http500_throwsProviderUnavailableException() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(500);
        stubSend(httpClient, httpResponse);

        OpenAiAdapter adapter = createAdapterWithClient(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(
                ProviderUnavailableException.class,
                () -> adapter.complete(minimalRequest(), minimalConfig("https://api.example.com")));
    }

    @Test
    void emptyResponseBody_returnsEmptyCompletion() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("   ");
        stubSend(httpClient, httpResponse);

        OpenAiAdapter adapter = createAdapterWithClient(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatCompletionResponse out =
                adapter.complete(minimalRequest(), minimalConfig("https://api.example.com"));

        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void ioException_throwsProviderUnavailableException() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        doThrow(new IOException("network"))
                .when(httpClient)
                .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        OpenAiAdapter adapter = createAdapterWithClient(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(
                ProviderUnavailableException.class,
                () -> adapter.complete(minimalRequest(), minimalConfig("https://api.example.com")));
    }

    @Test
    void interruptedException_throwsProviderUnavailableExceptionAndSetsInterruptFlag() throws Exception {
        Thread.interrupted();
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        doThrow(new InterruptedException("stopped"))
                .when(httpClient)
                .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        OpenAiAdapter adapter = createAdapterWithClient(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(
                ProviderUnavailableException.class,
                () -> adapter.complete(minimalRequest(), minimalConfig("https://api.example.com")));
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }

    @Test
    void invalidCredentialsJson_throwsIllegalStateException() {
        when(encryptionService.decrypt(any())).thenReturn("not-json");

        OpenAiAdapter adapter = createAdapterWithClient(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> adapter.complete(minimalRequest(), minimalConfig("https://api.example.com")));
        assertTrue(ex.getMessage().contains("Invalid credentials JSON"));
    }

    @Test
    void missingApiKey_throwsIllegalStateException() {
        when(encryptionService.decrypt(any())).thenReturn("{}");

        OpenAiAdapter adapter = createAdapterWithClient(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> adapter.complete(minimalRequest(), minimalConfig("https://api.example.com")));
        assertTrue(ex.getMessage().contains("Missing api_key"));
    }

    @Test
    void resolveChatCompletionsUrl_endsWithChatCompletions_unchanged() {
        assertEquals(
                "https://api.example.com/v1/chat/completions",
                OpenAiAdapter.resolveChatCompletionsUrl("https://api.example.com/v1/chat/completions"));
    }

    @Test
    void resolveChatCompletionsUrl_endsWithSlash_appendsV1() {
        assertEquals(
                "https://api.example.com/v1/chat/completions",
                OpenAiAdapter.resolveChatCompletionsUrl("https://api.example.com/"));
    }

    @Test
    void resolveChatCompletionsUrl_noTrailingSlash_appendsSlashV1() {
        assertEquals(
                "https://api.example.com/v1/chat/completions",
                OpenAiAdapter.resolveChatCompletionsUrl("https://api.example.com"));
    }

    @Test
    void resolveChatCompletionsUrl_nullUrl_returnsDefault() {
        assertEquals("/v1/chat/completions", OpenAiAdapter.resolveChatCompletionsUrl(null));
    }

    @Test
    void nullResponseBody_returnsEmpty() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(null);
        stubSend(httpClient, httpResponse);

        OpenAiAdapter adapter = createAdapterWithClient(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ChatCompletionResponse out =
                adapter.complete(minimalRequest(), minimalConfig("https://api.example.com"));

        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void requestSerializationFailure_throwsIllegalStateException() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        ObjectMapper spyMapper = Mockito.spy(new ObjectMapper());
        Mockito.doThrow(new JsonProcessingException("boom") {})
                .when(spyMapper)
                .writeValueAsString(Mockito.any());

        OpenAiAdapter adapter = createAdapterWithClient(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = spyMapper;

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> adapter.complete(minimalRequest(), minimalConfig("https://api.example.com")));
        assertTrue(ex.getMessage().contains("Failed to serialize request"));
    }

    @Test
    void malformedJsonResponseBody_throwsProviderUnavailableException() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("not-json");
        stubSend(httpClient, httpResponse);

        OpenAiAdapter adapter = createAdapterWithClient(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        ProviderUnavailableException ex =
                assertThrows(
                        ProviderUnavailableException.class,
                        () -> adapter.complete(minimalRequest(), minimalConfig("https://api.example.com")));
        assertTrue(ex.getMessage().contains("OpenAI request failed"));
    }

    @Test
    void getProviderName_returnsOpenAi() {
        OpenAiAdapter adapter = createAdapterWithClient(httpClient);
        assertEquals(ProviderName.OPENAI, adapter.getProviderName());
    }

    @Test
    void credentialsJsonEmptyApiKey_throwsIllegalStateException() {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"\"}");

        OpenAiAdapter adapter = createAdapterWithClient(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> adapter.complete(minimalRequest(), minimalConfig("https://api.example.com")));
        assertTrue(ex.getMessage().contains("Missing api_key"));
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

    private static OpenAiAdapter createAdapterWithClient(HttpClient client) {
        HttpClient.Builder builder = mock(HttpClient.Builder.class);
        when(builder.connectTimeout(any())).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        try (MockedStatic<HttpClient> hs = mockStatic(HttpClient.class)) {
            hs.when(HttpClient::newBuilder).thenReturn(builder);
            return new OpenAiAdapter();
        }
    }

    private static ChatCompletionRequest minimalRequest() {
        ChatMessage m = new ChatMessage();
        m.setRole("user");
        m.setContent("hi");
        ChatCompletionRequest r = new ChatCompletionRequest();
        r.setMessages(List.of(m));
        return r;
    }

    private static LlmConfig minimalConfig(String endpoint) {
        LlmProvider p = new LlmProvider();
        p.setName(ProviderName.OPENAI);
        LlmConfig c = new LlmConfig();
        c.setProvider(p);
        c.setEndpointUrl(endpoint);
        c.setModelName("m");
        c.setCredentialsEncrypted("enc");
        return c;
    }
}
