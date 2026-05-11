package com.aibridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibridge.adapter.LlmProviderAdapter;
import com.aibridge.dto.admin.ValidationResultResponse;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.LlmProvider;
import com.aibridge.model.enums.ProviderName;
import jakarta.enterprise.inject.Instance;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
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
class LlmValidationServiceTest {

    @Mock
    LlmProviderAdapter adapter;

    @Mock
    Instance<LlmProviderAdapter> adapterInstances;

    @Mock
    EncryptionService encryptionService;

    @InjectMocks
    LlmValidationService llmValidationService;

    @BeforeEach
    void wireAdapters() throws Exception {
        when(encryptionService.decrypt(any())).thenReturn("{\"api_key\":\"test-key\"}");
        when(adapter.getProviderName()).thenReturn(ProviderName.OPENAI);
        when(adapterInstances.iterator()).thenAnswer(inv -> List.of(adapter).iterator());

        Field inst = LlmValidationService.class.getDeclaredField("adapterInstances");
        inst.setAccessible(true);
        inst.set(llmValidationService, adapterInstances);

        Field enc = LlmValidationService.class.getDeclaredField("encryptionService");
        enc.setAccessible(true);
        enc.set(llmValidationService, encryptionService);

        Method init = LlmValidationService.class.getDeclaredMethod("initAdapters");
        init.setAccessible(true);
        init.invoke(llmValidationService);
    }

    @Test
    void successfulValidation_returnsValidWithMessage() {
        LlmConfig config = configWithEncryptedCreds();

        ValidationResultResponse result = llmValidationService.validate(config);

        assertTrue(result.isValid());
        assertEquals("Connection successful", result.getMessage());
        verify(adapter).complete(any(), any());
    }

    @Test
    void failedValidation_whenAdapterThrows_returnsInvalidWithMessage() {
        LlmConfig config = configWithEncryptedCreds();
        when(adapter.complete(any(), any())).thenThrow(new IllegalStateException("bad endpoint"));

        ValidationResultResponse result = llmValidationService.validate(config);

        assertFalse(result.isValid());
        assertEquals("bad endpoint", result.getMessage());
    }

    @Test
    void failedValidation_whenDecryptFails_returnsInvalid() {
        LlmConfig config = configWithEncryptedCreds();
        when(encryptionService.decrypt(any()))
                .thenThrow(new com.aibridge.exception.EncryptionException("bad cipher", new RuntimeException()));

        ValidationResultResponse result = llmValidationService.validate(config);

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("bad cipher") || result.getMessage().length() > 0);
    }

    @Test
    void validation_skipsDecryptWhenCredentialsBlank() {
        LlmConfig config = new LlmConfig();
        config.setCredentialsEncrypted("  ");
        LlmProvider p = new LlmProvider();
        p.setName(ProviderName.OPENAI);
        config.setProvider(p);

        ValidationResultResponse result = llmValidationService.validate(config);

        assertTrue(result.isValid());
        verify(encryptionService, org.mockito.Mockito.never()).decrypt(any());
    }

    @Test
    void validation_skipsDecryptWhenCredentialsNull() {
        LlmConfig config = new LlmConfig();
        config.setCredentialsEncrypted(null);
        LlmProvider p = new LlmProvider();
        p.setName(ProviderName.OPENAI);
        p.setId(UUID.randomUUID());
        config.setProvider(p);
        config.setEndpointUrl("https://api.example.com/v1/chat/completions");
        config.setModelName("m");

        ValidationResultResponse result = llmValidationService.validate(config);

        assertTrue(result.isValid());
        verify(encryptionService, org.mockito.Mockito.never()).decrypt(any());
        verify(adapter).complete(any(), any());
    }

    @Test
    void noAdapterForProvider_returnsInvalid() {
        LlmConfig config = new LlmConfig();
        config.setCredentialsEncrypted("enc");
        LlmProvider p = new LlmProvider();
        p.setName(ProviderName.CEREBRAS);
        p.setId(UUID.randomUUID());
        config.setProvider(p);
        config.setEndpointUrl("https://api.example.com/v1/chat/completions");
        config.setModelName("m");

        ValidationResultResponse result = llmValidationService.validate(config);

        assertFalse(result.isValid());
        assertEquals("No adapter for provider: CEREBRAS", result.getMessage());
        verify(adapter, org.mockito.Mockito.never()).complete(any(), any());
    }

    private static LlmConfig configWithEncryptedCreds() {
        LlmProvider p = new LlmProvider();
        p.setName(ProviderName.OPENAI);
        p.setId(UUID.randomUUID());
        LlmConfig c = new LlmConfig();
        c.setProvider(p);
        c.setCredentialsEncrypted("enc");
        c.setEndpointUrl("https://api.example.com/v1/chat/completions");
        c.setModelName("m");
        return c;
    }
}
