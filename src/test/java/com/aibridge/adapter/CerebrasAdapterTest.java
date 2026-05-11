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
import com.aibridge.dto.openai.ChatMessage;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class CerebrasAdapterTest {

    @Mock
    EncryptionService encryptionService;

    @Mock
    HttpClient httpClient;

    @Mock
    HttpResponse<String> httpResponse;

    @Test
    void successfulCall_returnsParsedCompletion() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"ck\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body())
                .thenReturn(
                        """
                        {"id":"c1","object":"chat.completion","created":1,"model":"llama",\
                        "choices":[{"index":0,"message":{"role":"assistant","content":"fast"},\
                        "finish_reason":"stop"}]}\
                        """);
        stubSend(httpClient, httpResponse);

        CerebrasAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        var out = adapter.complete(minimalRequest(), cerebrasConfig());

        assertEquals("fast", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void http429_throwsProviderRateLimitException() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"ck\"}");
        when(httpResponse.statusCode()).thenReturn(429);
        stubSend(httpClient, httpResponse);

        CerebrasAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(ProviderRateLimitException.class, () -> adapter.complete(minimalRequest(), cerebrasConfig()));
    }

    @Test
    void http500_throwsProviderUnavailableException() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"ck\"}");
        when(httpResponse.statusCode()).thenReturn(500);
        stubSend(httpClient, httpResponse);

        CerebrasAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(
                ProviderUnavailableException.class, () -> adapter.complete(minimalRequest(), cerebrasConfig()));
    }

    @Test
    void blankBody_returnsEmptyCompletion() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"ck\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("");
        stubSend(httpClient, httpResponse);

        CerebrasAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        var out = adapter.complete(minimalRequest(), cerebrasConfig());

        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void ioException_throwsProviderUnavailableException() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"ck\"}");
        doThrow(new IOException("net"))
                .when(httpClient)
                .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        CerebrasAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(
                ProviderUnavailableException.class, () -> adapter.complete(minimalRequest(), cerebrasConfig()));
    }

    @Test
    void getProviderName_returnsCerebras() {
        CerebrasAdapter adapter = createAdapter(httpClient);
        assertEquals(ProviderName.CEREBRAS, adapter.getProviderName());
    }

    @Test
    void invalidCredentialsJson_throwsIllegalStateException() {
        when(encryptionService.decrypt(any())).thenReturn("{not-json");
        CerebrasAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class, () -> adapter.complete(minimalRequest(), cerebrasConfig()));
        assertTrue(ex.getMessage().contains("credentials"));
    }

    @Test
    void missingApiKey_throwsIllegalStateException() {
        when(encryptionService.decrypt(any())).thenReturn("{}");
        CerebrasAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(IllegalStateException.class, () -> adapter.complete(minimalRequest(), cerebrasConfig()));
    }

    @Test
    void blankApiKey_throwsIllegalStateException() {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"  \"}");
        CerebrasAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        assertThrows(IllegalStateException.class, () -> adapter.complete(minimalRequest(), cerebrasConfig()));
    }

    @Test
    void writeRequestJsonIOException_throwsIllegalStateException() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"ck\"}");

        ObjectMapper failingMapper =
                new ObjectMapper() {
                    @Override
                    public String writeValueAsString(Object value) throws JsonProcessingException {
                        throw new JsonProcessingException("fail") {};
                    }
                };

        CerebrasAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = failingMapper;

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> adapter.complete(minimalRequest(), cerebrasConfig()));
        assertTrue(ex.getMessage().contains("serialize"));
    }

    @Test
    void interruptedException_setsInterruptFlag() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"ck\"}");
        doThrow(new InterruptedException("intr"))
                .when(httpClient)
                .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        CerebrasAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        try {
            assertThrows(
                    ProviderUnavailableException.class, () -> adapter.complete(minimalRequest(), cerebrasConfig()));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void nullResponseBody_returnsEmptyCompletion() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"ck\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(null);
        stubSend(httpClient, httpResponse);

        CerebrasAdapter adapter = createAdapter(httpClient);
        adapter.encryptionService = encryptionService;
        adapter.objectMapper = new ObjectMapper();

        var out = adapter.complete(minimalRequest(), cerebrasConfig());

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

    private static CerebrasAdapter createAdapter(HttpClient client) {
        HttpClient.Builder builder = mock(HttpClient.Builder.class);
        when(builder.connectTimeout(any())).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        try (var hs = mockStatic(HttpClient.class)) {
            hs.when(HttpClient::newBuilder).thenReturn(builder);
            return new CerebrasAdapter();
        }
    }

    private static ChatCompletionRequest minimalRequest() {
        ChatMessage u = new ChatMessage();
        u.setRole("user");
        u.setContent("Hi");
        ChatCompletionRequest r = new ChatCompletionRequest();
        r.setMessages(List.of(u));
        return r;
    }

    private static LlmConfig cerebrasConfig() {
        LlmProvider p = new LlmProvider();
        p.setName(ProviderName.CEREBRAS);
        LlmConfig c = new LlmConfig();
        c.setProvider(p);
        c.setEndpointUrl("https://api.cerebras.ai/v1/chat/completions");
        c.setModelName("llama");
        c.setCredentialsEncrypted("enc");
        return c;
    }
}
