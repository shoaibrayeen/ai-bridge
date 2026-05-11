package com.aibridge.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibridge.cache.CacheProvider;
import com.aibridge.config.AiBridgeConfig;
import com.aibridge.config.RedisConfig;
import com.aibridge.exception.QueueTimeoutException;
import com.aibridge.model.LlmConfig;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequestPacingServiceTest {

    @Mock
    CacheProvider cacheProvider;

    @Mock
    AiBridgeConfig aiBridgeConfig;

    @InjectMocks
    RequestPacingService requestPacingService;

    @BeforeEach
    void stubQueueSettings() {
        when(aiBridgeConfig.getQueuePollIntervalMs()).thenReturn(1);
        when(aiBridgeConfig.getQueueTimeoutMs()).thenReturn(10_000);
        when(aiBridgeConfig.getQueueMaxDepth()).thenReturn(1000);
    }

    @Test
    void acquireSlot_whenTokenAcquired_succeeds() {
        UUID id = UUID.randomUUID();
        LlmConfig config = new LlmConfig();
        config.setId(id);
        config.setRpsLimit(10);
        config.setRpmLimit(null);
        config.setTpmLimit(null);
        config.setQueueTimeoutMs(5_000);

        when(cacheProvider.incr(anyString())).thenReturn(1L);
        when(cacheProvider.tryAcquireToken(anyString(), anyInt(), anyDouble(), anyLong()))
                .thenReturn(true);

        assertDoesNotThrow(() -> requestPacingService.acquireSlot(config));
        verify(cacheProvider, atLeastOnce()).decr(anyString());
    }

    @Test
    void acquireSlot_whenAlwaysDenied_throwsQueueTimeoutException() {
        UUID id = UUID.randomUUID();
        LlmConfig config = new LlmConfig();
        config.setId(id);
        config.setRpsLimit(5);
        config.setQueueTimeoutMs(15);

        when(cacheProvider.incr(anyString())).thenReturn(1L);
        when(cacheProvider.tryAcquireToken(anyString(), anyInt(), anyDouble(), anyLong()))
                .thenReturn(false);

        assertThrows(QueueTimeoutException.class, () -> requestPacingService.acquireSlot(config));
    }

    @Test
    void acquireSlot_withNullLimits_returnsImmediatelyWithoutTokenScripts() {
        LlmConfig config = new LlmConfig();
        config.setId(UUID.randomUUID());
        config.setRpsLimit(null);
        config.setRpmLimit(null);
        config.setTpmLimit(null);

        assertDoesNotThrow(() -> requestPacingService.acquireSlot(config));
        verify(cacheProvider, never()).tryAcquireToken(anyString(), anyInt(), anyDouble(), anyLong());
    }

    @Test
    void reconcileTokens_withTpmLimit_callsReconcileTpm() {
        UUID id = UUID.randomUUID();
        LlmConfig config = new LlmConfig();
        config.setId(id);
        config.setTpmLimit(100_000);

        requestPacingService.reconcileTokens(config, 42);

        verify(cacheProvider)
                .reconcileTpm(eq(RedisConfig.tokenBucketKey(id.toString(), "tpm")), eq(-42.0));
    }

    @Test
    void acquireSlot_withRpmLimit_callsTokenBucket() {
        UUID id = UUID.randomUUID();
        LlmConfig config = new LlmConfig();
        config.setId(id);
        config.setRpsLimit(null);
        config.setRpmLimit(60);
        config.setTpmLimit(null);
        config.setQueueTimeoutMs(5_000);

        when(cacheProvider.incr(anyString())).thenReturn(1L);
        when(cacheProvider.tryAcquireToken(anyString(), anyInt(), anyDouble(), anyLong()))
                .thenReturn(true);

        assertDoesNotThrow(() -> requestPacingService.acquireSlot(config));
        verify(cacheProvider, atLeastOnce()).tryAcquireToken(anyString(), anyInt(), anyDouble(), anyLong());
    }

    @Test
    void acquireSlot_withTpmLimit_callsTpmCheck() {
        UUID id = UUID.randomUUID();
        LlmConfig config = new LlmConfig();
        config.setId(id);
        config.setRpsLimit(null);
        config.setRpmLimit(null);
        config.setTpmLimit(100_000);
        config.setQueueTimeoutMs(5_000);

        when(cacheProvider.incr(anyString())).thenReturn(1L);
        when(cacheProvider.tryTpmCheck(anyString(), anyInt(), anyDouble(), anyLong()))
                .thenReturn(true);

        assertDoesNotThrow(() -> requestPacingService.acquireSlot(config));
        verify(cacheProvider, atLeastOnce()).tryTpmCheck(anyString(), anyInt(), anyDouble(), anyLong());
    }

    @Test
    void acquireSlot_queueDepthExceeded_throwsQueueTimeoutException() {
        UUID id = UUID.randomUUID();
        LlmConfig config = new LlmConfig();
        config.setId(id);
        config.setRpsLimit(10);
        config.setQueueTimeoutMs(5_000);

        when(cacheProvider.incr(anyString())).thenReturn(1001L);

        assertThrows(QueueTimeoutException.class, () -> requestPacingService.acquireSlot(config));
        verify(cacheProvider, atLeastOnce()).decr(anyString());
        verify(cacheProvider, never()).tryAcquireToken(anyString(), anyInt(), anyDouble(), anyLong());
    }

    @Test
    void acquireSlot_interruptedDuringSleep_throwsQueueTimeoutWithCauseAndRunsDecr() throws Exception {
        UUID id = UUID.randomUUID();
        LlmConfig config = new LlmConfig();
        config.setId(id);
        config.setRpsLimit(5);
        config.setRpmLimit(null);
        config.setTpmLimit(null);
        config.setQueueTimeoutMs(60_000);

        when(aiBridgeConfig.getQueuePollIntervalMs()).thenReturn(200);
        when(aiBridgeConfig.getQueueTimeoutMs()).thenReturn(10_000);
        when(cacheProvider.incr(anyString())).thenReturn(1L);
        when(cacheProvider.tryAcquireToken(anyString(), anyInt(), anyDouble(), anyLong()))
                .thenReturn(false);

        AtomicReference<Throwable> caught = new AtomicReference<>();
        Thread worker =
                new Thread(
                        () -> {
                            try {
                                requestPacingService.acquireSlot(config);
                            } catch (Throwable t) {
                                caught.set(t);
                            }
                        });
        worker.start();
        Thread.sleep(80);
        worker.interrupt();
        worker.join(10_000);

        assertNotNull(caught.get());
        assertInstanceOf(QueueTimeoutException.class, caught.get());
        assertInstanceOf(InterruptedException.class, caught.get().getCause());
        verify(cacheProvider, atLeastOnce()).decr(anyString());
    }

    @Test
    void acquireSlot_whenFirstAttemptFails_thenSucceeds() {
        UUID id = UUID.randomUUID();
        LlmConfig config = new LlmConfig();
        config.setId(id);
        config.setRpsLimit(10);
        config.setRpmLimit(null);
        config.setTpmLimit(null);
        config.setQueueTimeoutMs(5_000);

        when(cacheProvider.incr(anyString())).thenReturn(1L);
        when(cacheProvider.tryAcquireToken(anyString(), anyInt(), anyDouble(), anyLong()))
                .thenReturn(false, true);

        assertDoesNotThrow(() -> requestPacingService.acquireSlot(config));
        verify(cacheProvider, atLeastOnce()).decr(anyString());
    }

    @Test
    void acquireSlot_whenTpmCheckFirstFails_thenSucceeds() {
        UUID id = UUID.randomUUID();
        LlmConfig config = new LlmConfig();
        config.setId(id);
        config.setRpsLimit(null);
        config.setRpmLimit(null);
        config.setTpmLimit(100_000);
        config.setQueueTimeoutMs(5_000);

        when(cacheProvider.incr(anyString())).thenReturn(1L);
        when(cacheProvider.tryTpmCheck(anyString(), anyInt(), anyDouble(), anyLong()))
                .thenReturn(false, true);

        assertDoesNotThrow(() -> requestPacingService.acquireSlot(config));
        verify(cacheProvider, atLeastOnce()).decr(anyString());
    }

    @Test
    void reconcileTokens_withNullTpmLimit_doesNothing() {
        UUID id = UUID.randomUUID();
        LlmConfig config = new LlmConfig();
        config.setId(id);
        config.setTpmLimit(null);

        requestPacingService.reconcileTokens(config, 10);

        verify(cacheProvider, never()).reconcileTpm(anyString(), anyDouble());
    }

    @Test
    void reconcileTokens_withZeroTokens_doesNothing() {
        UUID id = UUID.randomUUID();
        LlmConfig config = new LlmConfig();
        config.setId(id);
        config.setTpmLimit(100_000);

        requestPacingService.reconcileTokens(config, 0);

        verify(cacheProvider, never()).reconcileTpm(anyString(), anyDouble());
    }

    @Test
    void acquireSlot_retries_untilTimeout() {
        UUID id = UUID.randomUUID();
        LlmConfig config = new LlmConfig();
        config.setId(id);
        config.setRpsLimit(10);
        config.setQueueTimeoutMs(100);

        when(cacheProvider.incr(anyString())).thenReturn(1L);
        when(cacheProvider.tryAcquireToken(anyString(), anyInt(), anyDouble(), anyLong()))
                .thenReturn(false);

        assertThrows(QueueTimeoutException.class, () -> requestPacingService.acquireSlot(config));
    }
}
