package com.aibridge.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibridge.dto.lineage.CompletionLineage;
import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatCompletionResponse;
import com.aibridge.dto.openai.ChatMessage;
import com.aibridge.dto.openai.Choice;
import com.aibridge.service.ChatCompletionService;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatCompletionResourceTest {

    @Mock
    ChatCompletionService chatCompletionService;

    @InjectMocks
    ChatCompletionResource chatCompletionResource;

    @Test
    void validRequest_returns200() {
        ChatMessage m = new ChatMessage();
        m.setRole("user");
        m.setContent("hello");
        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setMessages(List.of(m));

        ChatCompletionResponse body = new ChatCompletionResponse();
        Choice c = new Choice();
        ChatMessage assistant = new ChatMessage();
        assistant.setRole("assistant");
        assistant.setContent("world");
        c.setMessage(assistant);
        body.setChoices(List.of(c));

        when(chatCompletionService.complete(eq("tenant-a"), eq("chat"), any())).thenReturn(body);

        Response response = chatCompletionResource.complete("tenant-a", "chat", null, req);

        assertEquals(200, response.getStatus());
        ChatCompletionResponse entity = (ChatCompletionResponse) response.getEntity();
        assertEquals("world", entity.getChoices().get(0).getMessage().getContent());
        verify(chatCompletionService).complete("tenant-a", "chat", req);
    }

    @Test
    void missingXFeatureHeader_returns400() {
        ChatCompletionRequest req = sampleRequest();

        Response response = chatCompletionResource.complete("tenant-a", null, null, req);

        assertEquals(400, response.getStatus());
    }

    @Test
    void blankFeatureHeader_returns400() {
        ChatCompletionRequest req = sampleRequest();

        Response response = chatCompletionResource.complete("tenant-a", "   ", null, req);

        assertEquals(400, response.getStatus());
    }

    @Test
    void invalidFeaturePattern_returns400() {
        ChatCompletionRequest req = sampleRequest();

        Response response = chatCompletionResource.complete("tenant-a", "bad feature!", null, req);

        assertEquals(400, response.getStatus());
    }

    @Test
    void invalidTenantIdPattern_returns400() {
        ChatCompletionRequest req = sampleRequest();

        Response response = chatCompletionResource.complete("bad tenant!", "chat", null, req);

        assertEquals(400, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> entity = (Map<String, String>) response.getEntity();
        assertEquals("Invalid X-Tenant-ID header", entity.get("error"));
    }

    @Test
    void nullTenantId_resolvedToNull_returns200() {
        ChatCompletionRequest req = sampleRequest();
        ChatCompletionResponse body = new ChatCompletionResponse();
        Choice c = new Choice();
        ChatMessage assistant = new ChatMessage();
        assistant.setRole("assistant");
        assistant.setContent("ok");
        c.setMessage(assistant);
        body.setChoices(List.of(c));

        when(chatCompletionService.complete(eq(null), eq("chat"), any())).thenReturn(body);

        Response response = chatCompletionResource.complete(null, "chat", null, req);

        assertEquals(200, response.getStatus());
        verify(chatCompletionService).complete(eq(null), eq("chat"), any());
    }

    @Test
    void blankTenantId_resolvedToNull_returns200() {
        ChatCompletionRequest req = sampleRequest();
        ChatCompletionResponse body = new ChatCompletionResponse();
        Choice c = new Choice();
        ChatMessage assistant = new ChatMessage();
        assistant.setRole("assistant");
        assistant.setContent("ok");
        c.setMessage(assistant);
        body.setChoices(List.of(c));

        when(chatCompletionService.complete(eq(null), eq("chat"), any())).thenReturn(body);

        Response response = chatCompletionResource.complete("  ", "chat", null, req);

        assertEquals(200, response.getStatus());
        verify(chatCompletionService).complete(eq(null), eq("chat"), any());
    }

    @Test
    void validTenantAndFeature_passedToService() {
        ChatCompletionRequest req = sampleRequest();
        ChatCompletionResponse body = new ChatCompletionResponse();
        Choice c = new Choice();
        ChatMessage assistant = new ChatMessage();
        assistant.setRole("assistant");
        assistant.setContent("x");
        c.setMessage(assistant);
        body.setChoices(List.of(c));

        when(chatCompletionService.complete(eq("tenant-ok"), eq("my-feature"), any())).thenReturn(body);

        Response response = chatCompletionResource.complete("tenant-ok", "my-feature", null, req);

        assertEquals(200, response.getStatus());
        verify(chatCompletionService).complete("tenant-ok", "my-feature", req);
    }

    // ---------- gateway model pinning ----------

    @Test
    void gatewayModelName_withoutFeatureHeader_isAccepted() {
        ChatCompletionRequest req = sampleRequest();
        req.setModel("ai-bridge-2-claude-sonnet-6");
        when(chatCompletionService.complete(eq("tenant-a"), eq(null), any()))
                .thenReturn(bodyWithContent("pinned"));

        Response response = chatCompletionResource.complete("tenant-a", null, null, req);

        assertEquals(200, response.getStatus());
        verify(chatCompletionService).complete("tenant-a", null, req);
    }

    @Test
    void gatewayModelName_withBlankFeatureHeader_isAccepted() {
        ChatCompletionRequest req = sampleRequest();
        req.setModel("ai-bridge-1-gpt-4o");
        when(chatCompletionService.complete(eq(null), eq("  "), any()))
                .thenReturn(bodyWithContent("pinned"));

        Response response = chatCompletionResource.complete(null, "  ", null, req);

        assertEquals(200, response.getStatus());
    }

    @Test
    void providerModelName_withoutFeatureHeader_stillRequiresFeature() {
        // An OpenAI client must send some model; a provider-native one must not bypass routing.
        ChatCompletionRequest req = sampleRequest();
        req.setModel("gpt-4o");

        Response response = chatCompletionResource.complete("tenant-a", null, null, req);

        assertEquals(400, response.getStatus());
    }

    @Test
    void gatewayModelName_stillRejectsMalformedTenantHeader() {
        ChatCompletionRequest req = sampleRequest();
        req.setModel("ai-bridge-1-gpt-4o");

        Response response = chatCompletionResource.complete("bad tenant!", null, null, req);

        assertEquals(400, response.getStatus());
    }

    // ---------- lineage opt-in ----------

    @Test
    void lineageIsStrippedByDefault() {
        ChatCompletionRequest req = sampleRequest();
        ChatCompletionResponse body = bodyWithContent("x");
        body.setLineage(sampleLineage());
        when(chatCompletionService.complete(eq("t"), eq("chat"), any())).thenReturn(body);

        Response response = chatCompletionResource.complete("t", "chat", null, req);

        ChatCompletionResponse entity = (ChatCompletionResponse) response.getEntity();
        assertNull(entity.getLineage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", " true ", "1"})
    void lineageIsReturnedWhenHeaderIsTruthy(String header) {
        ChatCompletionRequest req = sampleRequest();
        ChatCompletionResponse body = bodyWithContent("x");
        body.setLineage(sampleLineage());
        when(chatCompletionService.complete(eq("t"), eq("chat"), any())).thenReturn(body);

        Response response = chatCompletionResource.complete("t", "chat", header, req);

        ChatCompletionResponse entity = (ChatCompletionResponse) response.getEntity();
        assertNotNull(entity.getLineage());
        assertEquals("req-1", entity.getLineage().getRequestId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "0", "yes", "", "  "})
    void lineageIsStrippedWhenHeaderIsNotTruthy(String header) {
        ChatCompletionRequest req = sampleRequest();
        ChatCompletionResponse body = bodyWithContent("x");
        body.setLineage(sampleLineage());
        when(chatCompletionService.complete(eq("t"), eq("chat"), any())).thenReturn(body);

        Response response = chatCompletionResource.complete("t", "chat", header, req);

        ChatCompletionResponse entity = (ChatCompletionResponse) response.getEntity();
        assertNull(entity.getLineage());
    }

    private static CompletionLineage sampleLineage() {
        CompletionLineage lineage = new CompletionLineage();
        lineage.setRequestId("req-1");
        lineage.setRouting(CompletionLineage.ROUTING_FEATURE);
        return lineage;
    }

    private static ChatCompletionResponse bodyWithContent(String content) {
        ChatCompletionResponse body = new ChatCompletionResponse();
        Choice c = new Choice();
        ChatMessage assistant = new ChatMessage();
        assistant.setRole("assistant");
        assistant.setContent(content);
        c.setMessage(assistant);
        body.setChoices(List.of(c));
        return body;
    }

    private static ChatCompletionRequest sampleRequest() {
        ChatMessage m = new ChatMessage();
        m.setRole("user");
        m.setContent("x");
        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setMessages(List.of(m));
        return req;
    }
}
