package com.aibridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.aibridge.dto.lineage.AttemptOutcome;
import com.aibridge.dto.lineage.CallAttempt;
import com.aibridge.dto.lineage.CompletionLineage;
import com.aibridge.dto.openai.ChatCompletionChunk;
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
import java.util.stream.Stream;
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
        // The empty shape carries the gateway identity, like every other response.
        assertEquals(cfg.getGatewayModelName(), out.getModel());
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
        // The empty shape carries the gateway identity, like every other response.
        assertEquals(cfg.getGatewayModelName(), out.getModel());
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

    // =========================================================================
    // Gateway model pinning — DAO resolution is mocked, LLM calls are mocked.
    // =========================================================================

    @Test
    void modelPinned_resolvesByGatewayNameAndSkipsFeatureRouting() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveByGatewayModelName("tenant", "ai-bridge-1-m1"))
                .thenReturn(List.of(cfg));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), eq(cfg))).thenReturn(merged);
        when(openAiAdapter.complete(merged, cfg)).thenReturn(responseWithContent("pinned"));

        ChatCompletionRequest req = userRequest();
        req.setModel("ai-bridge-1-m1");

        ChatCompletionResponse out = chatCompletionService.complete("tenant", "feat", req);

        assertEquals("pinned", out.getChoices().get(0).getMessage().getContent());
        verify(configResolverService).resolveByGatewayModelName("tenant", "ai-bridge-1-m1");
        verify(configResolverService, never()).resolveChain(any(), any());
    }

    @Test
    void providerModelInPayload_stillUsesFeatureRouting() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveChain("tenant", "feat")).thenReturn(List.of(cfg));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), eq(cfg))).thenReturn(merged);
        when(openAiAdapter.complete(merged, cfg)).thenReturn(responseWithContent("ok"));

        ChatCompletionRequest req = userRequest();
        req.setModel("gpt-4o");

        chatCompletionService.complete("tenant", "feat", req);

        verify(configResolverService).resolveChain("tenant", "feat");
        verify(configResolverService, never()).resolveByGatewayModelName(any(), any());
    }

    @Test
    void responseEchoesGatewayModelNameNotProviderModel() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(cfg));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);
        ChatCompletionResponse provider = responseWithContent("ok");
        provider.setModel("gpt-4o");
        when(openAiAdapter.complete(merged, cfg)).thenReturn(provider);

        ChatCompletionResponse out = chatCompletionService.complete("t", "f", userRequest());

        assertEquals("ai-bridge-1-m1", out.getModel());
    }

    @Test
    void emptyResponseAlsoCarriesGatewayModelName() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(cfg));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);
        when(openAiAdapter.complete(merged, cfg)).thenReturn(responseWithContent("   "));

        ChatCompletionResponse out = chatCompletionService.complete("t", "f", userRequest());

        assertEquals("ai-bridge-1-m1", out.getModel());
    }

    // =========================================================================
    // Lineage — one record per attempt, with token detail.
    // =========================================================================

    @Test
    void lineage_singleSuccessfulAttempt_recordsIdentityOutcomeAndTokens() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveChain("tenant", "feat")).thenReturn(List.of(cfg));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), eq(cfg))).thenReturn(merged);
        when(openAiAdapter.complete(merged, cfg))
                .thenReturn(responseWithContentAndUsage("ok", usage(10, 7, 17)));

        ChatCompletionResponse out = chatCompletionService.complete("tenant", "feat", userRequest());

        CompletionLineage lineage = out.getLineage();
        assertNotNull(lineage);
        assertEquals(CompletionLineage.ROUTING_FEATURE, lineage.getRouting());
        assertEquals("tenant", lineage.getTenantId());
        assertEquals("feat", lineage.getFeature());
        assertEquals(1, lineage.getChainLength());
        assertEquals(1, lineage.getAttempts().size());

        CallAttempt attempt = lineage.getAttempts().get(0);
        assertEquals(1, attempt.getSequence());
        assertEquals(AttemptOutcome.SUCCESS, attempt.getOutcome());
        assertEquals("ai-bridge-1-m1", attempt.getGatewayModelName());
        assertEquals("m1", attempt.getModelName());
        assertEquals("OPENAI", attempt.getProvider());
        assertEquals(cfg.getId().toString(), attempt.getConfigId());
        assertEquals(10, attempt.getPromptTokens());
        assertEquals(7, attempt.getCompletionTokens());
        assertEquals(17, attempt.getTotalTokens());
        assertEquals(17, lineage.getBilledTotalTokens());
    }

    @Test
    void lineage_modelPinnedRoutingIsLabelled() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveByGatewayModelName(any(), eq("ai-bridge-1-m1")))
                .thenReturn(List.of(cfg));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);
        when(openAiAdapter.complete(merged, cfg)).thenReturn(responseWithContent("ok"));

        ChatCompletionRequest req = userRequest();
        req.setModel("ai-bridge-1-m1");

        ChatCompletionResponse out = chatCompletionService.complete("t", null, req);

        assertEquals(CompletionLineage.ROUTING_MODEL, out.getLineage().getRouting());
    }

    @Test
    void lineage_queueTimeoutThenSuccess_recordsBothAttemptsInOrder() {
        LlmConfig first = openAiConfig();
        LlmConfig second = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(first, second));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);
        doThrow(new QueueTimeoutException(first.getId().toString()))
                .when(requestPacingService)
                .acquireSlot(first);
        when(openAiAdapter.complete(merged, second))
                .thenReturn(responseWithContentAndUsage("second", usage(3, 4, 7)));

        ChatCompletionResponse out = chatCompletionService.complete("t", "f", userRequest());

        CompletionLineage lineage = out.getLineage();
        assertEquals(2, lineage.getAttempts().size());
        assertEquals(AttemptOutcome.QUEUE_TIMEOUT, lineage.getAttempts().get(0).getOutcome());
        assertEquals(1, lineage.getAttempts().get(0).getSequence());
        assertNull(lineage.getAttempts().get(0).getTotalTokens());
        assertEquals(AttemptOutcome.SUCCESS, lineage.getAttempts().get(1).getOutcome());
        assertEquals(2, lineage.getAttempts().get(1).getSequence());
        assertEquals(7, lineage.getBilledTotalTokens());
    }

    @Test
    void lineage_rateLimitedAttemptIsRecordedWithProviderDetail() {
        LlmConfig first = openAiConfig();
        LlmConfig second = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(first, second));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);
        when(openAiAdapter.complete(merged, first))
                .thenThrow(new ProviderRateLimitException("openai said 429"));
        when(openAiAdapter.complete(merged, second)).thenReturn(responseWithContent("ok"));

        ChatCompletionResponse out = chatCompletionService.complete("t", "f", userRequest());

        CallAttempt failed = out.getLineage().getAttempts().get(0);
        assertEquals(AttemptOutcome.RATE_LIMITED, failed.getOutcome());
        assertTrue(failed.getDetail().contains("429"));
    }

    @Test
    void lineage_unavailableAttemptIsRecorded() {
        LlmConfig first = openAiConfig();
        LlmConfig second = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(first, second));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);
        when(openAiAdapter.complete(merged, first))
                .thenThrow(new ProviderUnavailableException("connect timeout"));
        when(openAiAdapter.complete(merged, second)).thenReturn(responseWithContent("ok"));

        ChatCompletionResponse out = chatCompletionService.complete("t", "f", userRequest());

        assertEquals(AttemptOutcome.UNAVAILABLE, out.getLineage().getAttempts().get(0).getOutcome());
    }

    @Test
    void lineage_missingAdapterIsRecordedAsNoAdapter() {
        LlmConfig unsupported = cerebrasConfig();
        LlmConfig supported = openAiConfig();
        when(configResolverService.resolveChain(any(), any()))
                .thenReturn(List.of(unsupported, supported));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);
        when(openAiAdapter.complete(merged, supported)).thenReturn(responseWithContent("ok"));

        ChatCompletionResponse out = chatCompletionService.complete("t", "f", userRequest());

        assertEquals(AttemptOutcome.NO_ADAPTER, out.getLineage().getAttempts().get(0).getOutcome());
        assertEquals(AttemptOutcome.SUCCESS, out.getLineage().getAttempts().get(1).getOutcome());
    }

    @Test
    void lineage_emptyResponseIsTerminalAndRecorded() {
        LlmConfig first = openAiConfig();
        LlmConfig second = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(first, second));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);
        when(openAiAdapter.complete(merged, first)).thenReturn(responseWithContent(""));

        ChatCompletionResponse out = chatCompletionService.complete("t", "f", userRequest());

        assertEquals(1, out.getLineage().getAttempts().size());
        assertEquals(AttemptOutcome.EMPTY_RESPONSE, out.getLineage().getAttempts().get(0).getOutcome());
        // The second config is never touched — an empty answer is not a failover trigger.
        verify(openAiAdapter, never()).complete(merged, second);
    }

    @Test
    void lineage_tokensAreSummedAcrossEveryAttemptNotJustTheSuccessfulOne() {
        LlmConfig first = openAiConfig();
        LlmConfig second = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(first, second));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);
        // A provider can bill for a call that still ends in a rate-limit refusal downstream;
        // the gateway must not silently drop those tokens.
        when(openAiAdapter.complete(merged, first))
                .thenThrow(new ProviderUnavailableException("5xx"));
        when(openAiAdapter.complete(merged, second))
                .thenReturn(responseWithContentAndUsage("ok", usage(100, 50, 150)));

        ChatCompletionResponse out = chatCompletionService.complete("t", "f", userRequest());

        assertEquals(150, out.getLineage().getBilledTotalTokens());
        assertEquals(2, out.getLineage().getAttempts().size());
    }

    @Test
    void lineage_logStringNamesEveryAttemptInOrder() {
        LlmConfig first = openAiConfig();
        LlmConfig second = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(first, second));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);
        doThrow(new QueueTimeoutException(first.getId().toString()))
                .when(requestPacingService)
                .acquireSlot(first);
        when(openAiAdapter.complete(merged, second)).thenReturn(responseWithContent("ok"));

        ChatCompletionResponse out = chatCompletionService.complete("t", "f", userRequest());

        String log = out.getLineage().toLogString();
        assertTrue(log.contains("routing=feature"), log);
        assertTrue(log.contains("1:ai-bridge-1-m1/OPENAI=QUEUE_TIMEOUT"), log);
        assertTrue(log.contains("2:ai-bridge-1-m1/OPENAI=SUCCESS"), log);
        assertTrue(log.indexOf("QUEUE_TIMEOUT") < log.indexOf("SUCCESS"), log);
    }

    @Test
    void chainExhausted_throwsAndStillWalkedEveryConfig() {
        LlmConfig first = openAiConfig();
        LlmConfig second = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(first, second));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);
        when(openAiAdapter.complete(merged, first)).thenThrow(new ProviderRateLimitException("429"));
        when(openAiAdapter.complete(merged, second))
                .thenThrow(new ProviderUnavailableException("503"));

        assertThrows(
                ProviderUnavailableException.class,
                () -> chatCompletionService.complete("t", "f", userRequest()));

        verify(openAiAdapter).complete(merged, first);
        verify(openAiAdapter).complete(merged, second);
    }

    private static Usage usage(int prompt, int completion, int total) {
        Usage u = new Usage();
        u.setPromptTokens(prompt);
        u.setCompletionTokens(completion);
        u.setTotalTokens(total);
        return u;
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
        c.setModelSlug("m1");
        c.setModelSequence(1);
        c.setGatewayModelName("ai-bridge-1-m1");
        return c;
    }

    private static LlmConfig cerebrasConfig() {
        LlmProvider p = new LlmProvider();
        p.setName(ProviderName.CEREBRAS);
        LlmConfig c = new LlmConfig();
        c.setId(UUID.randomUUID());
        c.setProvider(p);
        c.setModelName("cb1");
        c.setModelSlug("cb1");
        c.setModelSequence(1);
        c.setGatewayModelName("ai-bridge-1-cb1");
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

    // =========================================================================
    // Streaming — adapter and DAO both mocked.
    // =========================================================================

    @Test
    void stream_returnsChunksFromTheFirstHealthyConfig() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveChain("t", "f")).thenReturn(List.of(cfg));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), eq(cfg))).thenReturn(merged);
        when(openAiAdapter.completeStream(merged, cfg))
                .thenReturn(Stream.of(
                        ChatCompletionChunk.roleChunk("id", "ai-bridge-1-m1"),
                        ChatCompletionChunk.contentChunk("id", "ai-bridge-1-m1", "hi")));

        ChatCompletionService.StreamingCompletion out =
                chatCompletionService.completeStream("t", "f", userRequest());

        List<ChatCompletionChunk> chunks = out.chunks().toList();
        assertEquals(2, chunks.size());
        assertEquals("hi", chunks.get(1).getChoices().get(0).getDelta().getContent());
        verify(requestPacingService).acquireSlot(cfg);
    }

    @Test
    void stream_failsOverWhenEstablishingTheStreamIsRateLimited() {
        LlmConfig first = openAiConfig();
        LlmConfig second = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(first, second));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);
        when(openAiAdapter.completeStream(merged, first))
                .thenThrow(new ProviderRateLimitException("429"));
        when(openAiAdapter.completeStream(merged, second))
                .thenReturn(Stream.of(ChatCompletionChunk.contentChunk("id", "m", "ok")));

        ChatCompletionService.StreamingCompletion out =
                chatCompletionService.completeStream("t", "f", userRequest());

        assertEquals(1, out.chunks().toList().size());
        assertEquals(AttemptOutcome.RATE_LIMITED, out.lineage().getAttempts().get(0).getOutcome());
        assertEquals(AttemptOutcome.SUCCESS, out.lineage().getAttempts().get(1).getOutcome());
    }

    @Test
    void stream_failsOverOnQueueTimeout() {
        LlmConfig first = openAiConfig();
        LlmConfig second = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(first, second));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);
        doThrow(new QueueTimeoutException(first.getId().toString()))
                .when(requestPacingService)
                .acquireSlot(first);
        when(openAiAdapter.completeStream(merged, second))
                .thenReturn(Stream.of(ChatCompletionChunk.contentChunk("id", "m", "ok")));

        ChatCompletionService.StreamingCompletion out =
                chatCompletionService.completeStream("t", "f", userRequest());

        assertEquals(AttemptOutcome.QUEUE_TIMEOUT, out.lineage().getAttempts().get(0).getOutcome());
        verify(openAiAdapter, never()).completeStream(merged, first);
    }

    @Test
    void stream_chainExhaustedThrows() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(cfg));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);
        when(openAiAdapter.completeStream(merged, cfg))
                .thenThrow(new ProviderUnavailableException("503"));

        assertThrows(
                ProviderUnavailableException.class,
                () -> chatCompletionService.completeStream("t", "f", userRequest()));
    }

    @Test
    void stream_tokensFromTheTerminatingChunkLandInTheLineage() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(cfg));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);
        when(openAiAdapter.completeStream(merged, cfg))
                .thenReturn(Stream.of(
                        ChatCompletionChunk.contentChunk("id", "m", "hi"),
                        ChatCompletionChunk.finalChunk("id", "m", "stop", usage(11, 4, 15))));

        ChatCompletionService.StreamingCompletion out =
                chatCompletionService.completeStream("t", "f", userRequest());

        // Usage is only known once the client drains the stream.
        assertNull(out.lineage().getAttempts().get(0).getTotalTokens());
        out.chunks().forEach(c -> { });
        assertEquals(15, out.lineage().getAttempts().get(0).getTotalTokens());
        assertEquals(15, out.lineage().getBilledTotalTokens());
    }

    @Test
    void stream_honoursGatewayModelPinning() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveByGatewayModelName("t", "ai-bridge-1-m1"))
                .thenReturn(List.of(cfg));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);
        when(openAiAdapter.completeStream(merged, cfg))
                .thenReturn(Stream.of(ChatCompletionChunk.contentChunk("id", "m", "ok")));

        ChatCompletionRequest req = userRequest();
        req.setModel("ai-bridge-1-m1");
        req.setStream(true);

        ChatCompletionService.StreamingCompletion out =
                chatCompletionService.completeStream("t", null, req);

        assertEquals(CompletionLineage.ROUTING_MODEL, out.lineage().getRouting());
        verify(configResolverService, never()).resolveChain(any(), any());
    }

    // =========================================================================
    // Unexpected failures: recorded, logged, rethrown — never failed over.
    // =========================================================================

    @Test
    void unexpectedFailure_isRecordedAsErrorAndRethrownWithoutFailover() {
        LlmConfig first = openAiConfig();
        LlmConfig second = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(first, second));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);
        when(openAiAdapter.complete(merged, first))
                .thenThrow(new IllegalStateException("Invalid credentials JSON"));

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> chatCompletionService.complete("t", "f", userRequest()));

        assertEquals("Invalid credentials JSON", thrown.getMessage());
        // A bad key fails identically on every config, so trying the next one only hides the cause.
        verify(openAiAdapter, never()).complete(merged, second);
    }

    @Test
    void unexpectedFailure_duringStreamingAlsoRethrows() {
        LlmConfig first = openAiConfig();
        LlmConfig second = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(first, second));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);
        when(openAiAdapter.completeStream(merged, first))
                .thenThrow(new IllegalStateException("Missing api_key in credentials"));

        assertThrows(
                IllegalStateException.class,
                () -> chatCompletionService.completeStream("t", "f", userRequest()));

        verify(openAiAdapter, never()).completeStream(merged, second);
    }

    @Test
    void unexpectedFailure_stillCountsAsAnAttemptSoTheChainIsNotSilent() {
        LlmConfig cfg = openAiConfig();
        when(configResolverService.resolveChain(any(), any())).thenReturn(List.of(cfg));
        ChatCompletionRequest merged = new ChatCompletionRequest();
        when(parameterMergeService.merge(any(), any())).thenReturn(merged);
        when(openAiAdapter.complete(merged, cfg))
                .thenThrow(new IllegalStateException("boom"));

        assertThrows(
                IllegalStateException.class,
                () -> chatCompletionService.complete("t", "f", userRequest()));

        // The lineage is not returned on the throwing path, but the outcome exists so the logged
        // line names the failing config rather than going silent.
        verify(requestPacingService).acquireSlot(cfg);
    }
}
