package com.aibridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibridge.adapter.LlmProviderAdapter;
import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatCompletionResponse;
import com.aibridge.dto.openai.ChatMessage;
import com.aibridge.dto.openai.Choice;
import com.aibridge.dto.openai.Usage;
import com.aibridge.exception.ProviderRateLimitException;
import com.aibridge.exception.ProviderUnavailableException;
import com.aibridge.exception.QueueTimeoutException;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.LlmProvider;
import com.aibridge.model.enums.ProviderName;
import jakarta.enterprise.inject.Instance;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatCompletionServiceTest {

    @Mock
    ConfigResolverService configResolverService;

    @Mock
    ParameterMergeService parameterMergeService;

    @Mock
    RequestPacingService requestPacingService;

    @Mock
    LlmProviderAdapter openAiAdapter;

    @Mock
    Instance<LlmProviderAdapter> adapterInstances;

    @InjectMocks
    ChatCompletionService chatCompletionService;

    @BeforeEach
    void registerAdapter() throws Exception {
        when(openAiAdapter.getProviderName()).thenReturn(ProviderName.OPENAI);
        when(adapterInstances.iterator()).thenAnswer(inv -> List.of(openAiAdapter).iterator());

        Field f = ChatCompletionService.class.getDeclaredField("adapterInstances");
        f.setAccessible(true);
        f.set(chatCompletionService, adapterInstances);

        Method init = ChatCompletionService.class.getDeclaredMethod("initAdapters");
        init.setAccessible(true);
        init.invoke(chatCompletionService);
    }

    @Test
    void successfulCompletion_whenFirstConfigSucceeds_returnsAdapterResponse() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveChain("tenant", "feat")).thenReturn(List.of(cfg));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), eq(cfg))).thenReturn(merged);

        ChatCompletionResponse expected = responseWithContent("ok");
        when(openAiAdapter.complete(merged, cfg)).thenReturn(expected);

        ChatCompletionResponse out =
                chatCompletionService.complete("tenant", "feat", userRequest());

        assertEquals("ok", out.getChoices().get(0).getMessage().getContent());
        verify(requestPacingService).acquireSlot(cfg);
    }

    @Test
    void failover_firstConfigQueueTimeout_secondConfigSucceeds() {
        LlmConfig first = openAiConfig();
        LlmConfig second = openAiConfig();
        second.setId(UUID.randomUUID());
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(first, second));

        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);

        doThrow(new QueueTimeoutException(first.getId().toString()))
                .when(requestPacingService)
                .acquireSlot(first);

        ChatCompletionResponse expected = responseWithContent("from-second");
        when(openAiAdapter.complete(merged, second)).thenReturn(expected);

        ChatCompletionResponse out = chatCompletionService.complete("t", "f", userRequest());

        assertEquals("from-second", out.getChoices().get(0).getMessage().getContent());
        verify(requestPacingService).acquireSlot(first);
        verify(requestPacingService).acquireSlot(second);
    }

    @Test
    void failover_firstConfigRateLimited_secondConfigSucceeds() {
        LlmConfig first = openAiConfig();
        LlmConfig second = openAiConfig();
        second.setId(UUID.randomUUID());
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(first, second));

        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);

        when(openAiAdapter.complete(merged, first)).thenThrow(new ProviderRateLimitException("429"));
        when(openAiAdapter.complete(merged, second)).thenReturn(responseWithContent("recovered"));

        ChatCompletionResponse out = chatCompletionService.complete("t", "f", userRequest());

        assertEquals("recovered", out.getChoices().get(0).getMessage().getContent());
        verify(openAiAdapter, times(2)).complete(any(), any());
    }

    @Test
    void allConfigsFail_throwsProviderUnavailableException() {
        LlmConfig first = openAiConfig();
        LlmConfig second = openAiConfig();
        second.setId(UUID.randomUUID());
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(first, second));

        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);

        when(openAiAdapter.complete(any(), any())).thenThrow(new ProviderUnavailableException("down"));

        assertThrows(
                ProviderUnavailableException.class,
                () -> chatCompletionService.complete("t", "f", userRequest()));
    }

    @Test
    void emptyAssistantContent_returnsEmptyCompletionShape() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(cfg));
        when(parameterMergeService.merge(any(), any())).thenReturn(new ChatCompletionRequest());

        ChatCompletionResponse blank = responseWithContent("   ");
        when(openAiAdapter.complete(any(), any())).thenReturn(blank);

        ChatCompletionResponse out = chatCompletionService.complete("t", "f", userRequest());

        assertEquals("", out.getChoices().get(0).getMessage().getContent());
        assertEquals(cfg.getModelName(), out.getModel());
    }

    @Test
    void noAdapterForProvider_skipsConfigAndTriesNext() {
        LlmConfig cerebras = cerebrasConfig();
        LlmConfig openai = openAiConfig();
        openai.setId(UUID.randomUUID());
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(cerebras, openai));

        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);

        ChatCompletionResponse expected = responseWithContent("from-openai");
        when(openAiAdapter.complete(eq(merged), eq(openai))).thenReturn(expected);

        ChatCompletionResponse out = chatCompletionService.complete("t", "f", userRequest());

        assertEquals("from-openai", out.getChoices().get(0).getMessage().getContent());
        verify(openAiAdapter, times(1)).complete(any(), eq(openai));
        verify(openAiAdapter, never()).complete(any(), eq(cerebras));
        verify(requestPacingService).acquireSlot(cerebras);
        verify(requestPacingService).acquireSlot(openai);
    }

    @Test
    void nullUsage_doesNotReconcileTokens() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(cfg));
        when(parameterMergeService.merge(any(), any())).thenReturn(new ChatCompletionRequest());

        ChatCompletionResponse resp = responseWithContentAndUsage("hi", null);
        when(openAiAdapter.complete(any(), any())).thenReturn(resp);

        chatCompletionService.complete("t", "f", userRequest());

        verify(requestPacingService, never()).reconcileTokens(any(), anyInt());
    }

    @Test
    void nullTotalTokens_doesNotReconcileTokens() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(cfg));
        when(parameterMergeService.merge(any(), any())).thenReturn(new ChatCompletionRequest());

        Usage usage = new Usage();
        usage.setTotalTokens(null);
        ChatCompletionResponse resp = responseWithContentAndUsage("hi", usage);
        when(openAiAdapter.complete(any(), any())).thenReturn(resp);

        chatCompletionService.complete("t", "f", userRequest());

        verify(requestPacingService, never()).reconcileTokens(any(), anyInt());
    }

    @Test
    void complete_withFineLogging_executesFineLogBranches() {
        Logger log = Logger.getLogger(ChatCompletionService.class.getName());
        Level previous = log.getLevel();
        try {
            log.setLevel(Level.FINE);
            LlmConfig cfg = openAiConfig();
            when(configResolverService.resolveChain("tenant", "feat")).thenReturn(List.of(cfg));
            ChatCompletionRequest merged = new ChatCompletionRequest();
            when(parameterMergeService.merge(any(), eq(cfg))).thenReturn(merged);
            when(openAiAdapter.complete(merged, cfg)).thenReturn(responseWithContent("ok"));

            chatCompletionService.complete("tenant", "feat", userRequest());

            verify(requestPacingService).reconcileTokens(eq(cfg), eq(10));
        } finally {
            log.setLevel(previous);
        }
    }

    @Test
    void emptyOrNullContent_nullResponse_returnsEmptyCompletion() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(cfg));
        when(parameterMergeService.merge(any(), any())).thenReturn(new ChatCompletionRequest());
        when(openAiAdapter.complete(any(), any())).thenReturn(null);

        ChatCompletionResponse out = chatCompletionService.complete("t", "f", userRequest());

        assertEquals("", out.getChoices().get(0).getMessage().getContent());
        assertEquals(cfg.getModelName(), out.getModel());
    }

    @Test
    void emptyOrNullContent_nullChoices_returnsEmptyCompletion() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(cfg));
        when(parameterMergeService.merge(any(), any())).thenReturn(new ChatCompletionRequest());
        ChatCompletionResponse resp = new ChatCompletionResponse();
        resp.setChoices(null);
        when(openAiAdapter.complete(any(), any())).thenReturn(resp);

        ChatCompletionResponse out = chatCompletionService.complete("t", "f", userRequest());

        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void emptyOrNullContent_emptyChoices_returnsEmptyCompletion() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(cfg));
        when(parameterMergeService.merge(any(), any())).thenReturn(new ChatCompletionRequest());
        ChatCompletionResponse resp = new ChatCompletionResponse();
        resp.setChoices(List.of());
        when(openAiAdapter.complete(any(), any())).thenReturn(resp);

        ChatCompletionResponse out = chatCompletionService.complete("t", "f", userRequest());

        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void emptyOrNullContent_nullFirstChoice_returnsEmptyCompletion() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(cfg));
        when(parameterMergeService.merge(any(), any())).thenReturn(new ChatCompletionRequest());
        ChatCompletionResponse resp = new ChatCompletionResponse();
        List<Choice> choices = new ArrayList<>();
        choices.add(null);
        resp.setChoices(choices);
        when(openAiAdapter.complete(any(), any())).thenReturn(resp);

        ChatCompletionResponse out = chatCompletionService.complete("t", "f", userRequest());

        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void emptyOrNullContent_nullMessage_returnsEmptyCompletion() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(cfg));
        when(parameterMergeService.merge(any(), any())).thenReturn(new ChatCompletionRequest());
        Choice choice = new Choice();
        choice.setMessage(null);
        ChatCompletionResponse resp = new ChatCompletionResponse();
        resp.setChoices(List.of(choice));
        when(openAiAdapter.complete(any(), any())).thenReturn(resp);

        ChatCompletionResponse out = chatCompletionService.complete("t", "f", userRequest());

        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void emptyOrNullContent_nullContent_returnsEmptyCompletion() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(cfg));
        when(parameterMergeService.merge(any(), any())).thenReturn(new ChatCompletionRequest());
        ChatMessage assistant = new ChatMessage();
        assistant.setRole("assistant");
        assistant.setContent(null);
        Choice choice = new Choice();
        choice.setMessage(assistant);
        ChatCompletionResponse resp = new ChatCompletionResponse();
        resp.setChoices(List.of(choice));
        when(openAiAdapter.complete(any(), any())).thenReturn(resp);

        ChatCompletionResponse out = chatCompletionService.complete("t", "f", userRequest());

        assertEquals("", out.getChoices().get(0).getMessage().getContent());
    }

    private static ChatCompletionRequest userRequest() {
        ChatMessage m = new ChatMessage();
        m.setRole("user");
        m.setContent("hi");
        ChatCompletionRequest r = new ChatCompletionRequest();
        r.setMessages(List.of(m));
        return r;
    }

    private static LlmConfig openAiConfig() {
        LlmProvider p = new LlmProvider();
        p.setName(ProviderName.OPENAI);
        LlmConfig c = new LlmConfig();
        c.setId(UUID.randomUUID());
        c.setProvider(p);
        c.setModelName("m1");
        return c;
    }

    private static LlmConfig cerebrasConfig() {
        LlmProvider p = new LlmProvider();
        p.setName(ProviderName.CEREBRAS);
        LlmConfig c = new LlmConfig();
        c.setId(UUID.randomUUID());
        c.setProvider(p);
        c.setModelName("cb1");
        return c;
    }

    private static ChatCompletionResponse responseWithContent(String text) {
        ChatMessage assistant = new ChatMessage();
        assistant.setRole("assistant");
        assistant.setContent(text);
        Choice choice = new Choice();
        choice.setMessage(assistant);
        Usage usage = new Usage();
        usage.setTotalTokens(10);
        ChatCompletionResponse r = new ChatCompletionResponse();
        r.setChoices(List.of(choice));
        r.setUsage(usage);
        return r;
    }

    private static ChatCompletionResponse responseWithContentAndUsage(String text, Usage usage) {
        ChatMessage assistant = new ChatMessage();
        assistant.setRole("assistant");
        assistant.setContent(text);
        Choice choice = new Choice();
        choice.setMessage(assistant);
        ChatCompletionResponse r = new ChatCompletionResponse();
        r.setChoices(List.of(choice));
        r.setUsage(usage);
        return r;
    }
}
