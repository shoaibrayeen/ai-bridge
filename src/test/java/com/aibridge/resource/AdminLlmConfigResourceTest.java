package com.aibridge.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibridge.dto.admin.LlmConfigRequest;
import com.aibridge.dto.admin.LlmConfigResponse;
import com.aibridge.dto.admin.ValidationResultResponse;
import com.aibridge.exception.EncryptionException;
import com.aibridge.filter.EndpointUrlValidator;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.LlmProvider;
import com.aibridge.model.enums.AuthType;
import com.aibridge.model.enums.ProviderName;
import com.aibridge.repository.FeatureRepository;
import com.aibridge.repository.LlmConfigRepository;
import com.aibridge.repository.LlmProviderRepository;
import com.aibridge.service.ConfigResolverService;
import com.aibridge.service.EncryptionService;
import com.aibridge.service.GatewayModelNameService;
import com.aibridge.service.LlmValidationService;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminLlmConfigResourceTest {

    @Mock
    LlmConfigRepository llmConfigRepository;

    @Mock
    LlmProviderRepository llmProviderRepository;

    @Mock
    FeatureRepository featureRepository;

    @Mock
    EncryptionService encryptionService;

    @Mock
    ConfigResolverService configResolverService;

    @Mock
    LlmValidationService llmValidationService;

    @Mock
    EndpointUrlValidator endpointUrlValidator;

    @Mock
    GatewayModelNameService gatewayModelNameService;

    @InjectMocks
    AdminLlmConfigResource adminLlmConfigResource;

    @Test
    void listConfigs_withoutFilters_returnsMappedResponses() {
        LlmConfig cfg = new LlmConfig();
        cfg.setId(UUID.randomUUID());
        cfg.setModelName("m");
        LlmProvider p = new LlmProvider();
        p.setName(ProviderName.OPENAI);
        cfg.setProvider(p);
        when(llmConfigRepository.listAll()).thenReturn(List.of(cfg));

        List<LlmConfigResponse> list = adminLlmConfigResource.listConfigs(null, null, null);

        assertEquals(1, list.size());
        assertEquals("m", list.get(0).getModelName());
    }

    @Test
    void createConfig_whenValidationSucceeds_returnsCreatedAndInvalidatesCache() {
        UUID providerId = UUID.randomUUID();
        LlmProvider provider = new LlmProvider();
        provider.setId(providerId);
        provider.setName(ProviderName.OPENAI);
        provider.setAuthType(AuthType.API_KEY);

        when(llmProviderRepository.findByIdOptional(providerId)).thenReturn(Optional.of(provider));
        when(encryptionService.encrypt("secret")).thenReturn("enc-blob");

        ValidationResultResponse ok = new ValidationResultResponse();
        ok.setValid(true);
        when(llmValidationService.validate(any(LlmConfig.class))).thenReturn(ok);

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(providerId);
        req.setModelName("gpt-4");
        req.setEndpointUrl("https://api.openai.com/v1/chat/completions");
        req.setCredentials("secret");
        req.setFeatures(List.of("chat"));

        Response response = adminLlmConfigResource.createConfig(req);

        assertEquals(201, response.getStatus());
        verify(llmConfigRepository).persist(any(LlmConfig.class));
        verify(configResolverService).invalidateCacheForConfig(any(LlmConfig.class));
    }

    @Test
    void createConfig_whenValidationFails_returns422() {
        UUID providerId = UUID.randomUUID();
        LlmProvider provider = new LlmProvider();
        provider.setId(providerId);
        provider.setName(ProviderName.OPENAI);
        provider.setAuthType(AuthType.API_KEY);
        when(llmProviderRepository.findByIdOptional(providerId)).thenReturn(Optional.of(provider));
        when(encryptionService.encrypt(any())).thenReturn("enc");

        ValidationResultResponse bad = new ValidationResultResponse();
        bad.setValid(false);
        bad.setMessage("cannot reach host");
        when(llmValidationService.validate(any(LlmConfig.class))).thenReturn(bad);

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(providerId);
        req.setModelName("gpt-4");
        req.setEndpointUrl("https://api.openai.com/v1/chat/completions");
        req.setCredentials("secret");
        req.setFeatures(List.of("chat"));

        Response response = adminLlmConfigResource.createConfig(req);

        assertEquals(422, response.getStatus());
        ValidationResultResponse entity = (ValidationResultResponse) response.getEntity();
        assertFalse(entity.isValid());
        assertEquals("cannot reach host", entity.getMessage());
    }

    @Test
    void deleteConfig_softDeletesAndInvalidatesCache() {
        UUID id = UUID.randomUUID();
        LlmConfig entity = new LlmConfig();
        entity.setId(id);
        entity.setActive(true);
        when(llmConfigRepository.findByIdOptional(id)).thenReturn(Optional.of(entity));

        Response response = adminLlmConfigResource.deleteConfig(id);

        assertEquals(204, response.getStatus());
        assertFalse(entity.isActive());
        verify(configResolverService).invalidateCacheForConfig(entity);
    }

    @Test
    void createConfig_whenEndpointValidationFails_returns400() {
        doThrow(new IllegalArgumentException("bad url")).when(endpointUrlValidator).validate(any());

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(UUID.randomUUID());
        req.setModelName("gpt-4");
        req.setEndpointUrl("not-a-url");
        req.setCredentials("secret");
        req.setFeatures(List.of("chat"));

        Response response = adminLlmConfigResource.createConfig(req);

        assertEquals(400, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertEquals("bad url", entity.get("error"));
    }

    @Test
    void createConfig_whenProviderNotFound_returns404() {
        UUID providerId = UUID.randomUUID();
        when(llmProviderRepository.findByIdOptional(providerId)).thenReturn(Optional.empty());

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(providerId);
        req.setModelName("gpt-4");
        req.setEndpointUrl("https://api.openai.com/v1/chat/completions");
        req.setCredentials("secret");
        req.setFeatures(List.of("chat"));

        Response response = adminLlmConfigResource.createConfig(req);

        assertEquals(404, response.getStatus());
    }

    @Test
    void getConfig_whenFound_returns200() {
        UUID id = UUID.randomUUID();
        LlmConfig cfg = new LlmConfig();
        cfg.setId(id);
        cfg.setModelName("m1");
        LlmProvider p = new LlmProvider();
        p.setName(ProviderName.OPENAI);
        cfg.setProvider(p);
        when(llmConfigRepository.findByIdOptional(id)).thenReturn(Optional.of(cfg));

        Response response = adminLlmConfigResource.getConfig(id);

        assertEquals(200, response.getStatus());
        LlmConfigResponse body = (LlmConfigResponse) response.getEntity();
        assertEquals("m1", body.getModelName());
    }

    @Test
    void getConfig_whenNotFound_returns404() {
        UUID id = UUID.randomUUID();
        when(llmConfigRepository.findByIdOptional(id)).thenReturn(Optional.empty());

        Response response = adminLlmConfigResource.getConfig(id);

        assertEquals(404, response.getStatus());
    }

    @Test
    void updateConfig_success() {
        UUID id = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        LlmProvider provider = new LlmProvider();
        provider.setId(providerId);
        provider.setName(ProviderName.OPENAI);
        provider.setAuthType(AuthType.API_KEY);

        LlmConfig entity = new LlmConfig();
        entity.setId(id);
        when(llmConfigRepository.findByIdOptional(id)).thenReturn(Optional.of(entity));
        when(llmProviderRepository.findByIdOptional(providerId)).thenReturn(Optional.of(provider));
        when(encryptionService.encrypt("secret")).thenReturn("enc-blob");

        ValidationResultResponse ok = new ValidationResultResponse();
        ok.setValid(true);
        when(llmValidationService.validate(any(LlmConfig.class))).thenReturn(ok);

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(providerId);
        req.setModelName("gpt-4");
        req.setEndpointUrl("https://api.openai.com/v1/chat/completions");
        req.setCredentials("secret");
        req.setFeatures(List.of("chat"));

        Response response = adminLlmConfigResource.updateConfig(id, req);

        assertEquals(200, response.getStatus());
        verify(configResolverService, times(2)).invalidateCacheForConfig(entity);
    }

    @Test
    void updateConfig_whenEndpointValidationFails_returns400() {
        UUID id = UUID.randomUUID();
        doThrow(new IllegalArgumentException("bad url")).when(endpointUrlValidator).validate(any());

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(UUID.randomUUID());
        req.setModelName("gpt-4");
        req.setEndpointUrl("not-a-url");
        req.setCredentials("secret");
        req.setFeatures(List.of("chat"));

        Response response = adminLlmConfigResource.updateConfig(id, req);

        assertEquals(400, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertEquals("bad url", entity.get("error"));
    }

    @Test
    void updateConfig_whenConfigNotFound_returns404() {
        UUID id = UUID.randomUUID();
        when(llmConfigRepository.findByIdOptional(id)).thenReturn(Optional.empty());

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(UUID.randomUUID());
        req.setModelName("gpt-4");
        req.setEndpointUrl("https://api.openai.com/v1/chat/completions");
        req.setCredentials("secret");
        req.setFeatures(List.of("chat"));

        Response response = adminLlmConfigResource.updateConfig(id, req);

        assertEquals(404, response.getStatus());
    }

    @Test
    void updateConfig_whenProviderNotFound_returns404() {
        UUID id = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        LlmConfig entity = new LlmConfig();
        entity.setId(id);
        when(llmConfigRepository.findByIdOptional(id)).thenReturn(Optional.of(entity));
        when(llmProviderRepository.findByIdOptional(providerId)).thenReturn(Optional.empty());

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(providerId);
        req.setModelName("gpt-4");
        req.setEndpointUrl("https://api.openai.com/v1/chat/completions");
        req.setCredentials("secret");
        req.setFeatures(List.of("chat"));

        Response response = adminLlmConfigResource.updateConfig(id, req);

        assertEquals(404, response.getStatus());
    }

    @Test
    void updateConfig_whenValidationFails_returns422() {
        UUID id = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        LlmProvider provider = new LlmProvider();
        provider.setId(providerId);
        provider.setName(ProviderName.OPENAI);
        provider.setAuthType(AuthType.API_KEY);

        LlmConfig entity = new LlmConfig();
        entity.setId(id);
        when(llmConfigRepository.findByIdOptional(id)).thenReturn(Optional.of(entity));
        when(llmProviderRepository.findByIdOptional(providerId)).thenReturn(Optional.of(provider));
        when(encryptionService.encrypt(any())).thenReturn("enc");

        ValidationResultResponse bad = new ValidationResultResponse();
        bad.setValid(false);
        bad.setMessage("cannot reach host");
        when(llmValidationService.validate(any(LlmConfig.class))).thenReturn(bad);

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(providerId);
        req.setModelName("gpt-4");
        req.setEndpointUrl("https://api.openai.com/v1/chat/completions");
        req.setCredentials("secret");
        req.setFeatures(List.of("chat"));

        Response response = adminLlmConfigResource.updateConfig(id, req);

        assertEquals(422, response.getStatus());
        ValidationResultResponse body = (ValidationResultResponse) response.getEntity();
        assertFalse(body.isValid());
        assertEquals("cannot reach host", body.getMessage());
    }

    @Test
    void deleteConfig_whenNotFound_returns404() {
        UUID id = UUID.randomUUID();
        when(llmConfigRepository.findByIdOptional(id)).thenReturn(Optional.empty());

        Response response = adminLlmConfigResource.deleteConfig(id);

        assertEquals(404, response.getStatus());
    }

    @Test
    void testConfig_success_returns200() {
        UUID providerId = UUID.randomUUID();
        LlmProvider provider = new LlmProvider();
        provider.setId(providerId);
        provider.setName(ProviderName.OPENAI);
        provider.setAuthType(AuthType.API_KEY);
        when(llmProviderRepository.findByIdOptional(providerId)).thenReturn(Optional.of(provider));
        when(encryptionService.encrypt("secret")).thenReturn("enc-blob");

        ValidationResultResponse result = new ValidationResultResponse();
        result.setValid(true);
        when(llmValidationService.validate(any(LlmConfig.class))).thenReturn(result);

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(providerId);
        req.setModelName("gpt-4");
        req.setEndpointUrl("https://api.openai.com/v1/chat/completions");
        req.setCredentials("secret");
        req.setFeatures(List.of("chat"));

        Response response = adminLlmConfigResource.testConfig(req);

        assertEquals(200, response.getStatus());
        assertEquals(result, response.getEntity());
    }

    @Test
    void testConfig_whenEndpointValidationFails_returns400() {
        doThrow(new IllegalArgumentException("bad url")).when(endpointUrlValidator).validate(any());

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(UUID.randomUUID());
        req.setModelName("gpt-4");
        req.setEndpointUrl("not-a-url");
        req.setCredentials("secret");
        req.setFeatures(List.of("chat"));

        Response response = adminLlmConfigResource.testConfig(req);

        assertEquals(400, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertEquals("bad url", entity.get("error"));
    }

    @Test
    void testConfig_whenProviderNotFound_returns404() {
        UUID providerId = UUID.randomUUID();
        when(llmProviderRepository.findByIdOptional(providerId)).thenReturn(Optional.empty());

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(providerId);
        req.setModelName("gpt-4");
        req.setEndpointUrl("https://api.openai.com/v1/chat/completions");
        req.setCredentials("secret");
        req.setFeatures(List.of("chat"));

        Response response = adminLlmConfigResource.testConfig(req);

        assertEquals(404, response.getStatus());
    }

    @Test
    void listConfigs_withTenantFilter() {
        LlmConfig cfg = new LlmConfig();
        cfg.setId(UUID.randomUUID());
        cfg.setModelName("m");
        LlmProvider p = new LlmProvider();
        p.setName(ProviderName.OPENAI);
        cfg.setProvider(p);
        when(llmConfigRepository.list(anyString(), any(Object[].class))).thenReturn(List.of(cfg));

        List<LlmConfigResponse> list = adminLlmConfigResource.listConfigs("tenant-1", null, null);

        assertEquals(1, list.size());
        assertEquals("m", list.get(0).getModelName());
    }

    @Test
    void listConfigs_withFeatureFilter() {
        LlmConfig cfg = new LlmConfig();
        cfg.setId(UUID.randomUUID());
        cfg.setModelName("m");
        LlmProvider p = new LlmProvider();
        p.setName(ProviderName.OPENAI);
        cfg.setProvider(p);
        when(llmConfigRepository.list(anyString(), any(Object[].class))).thenReturn(List.of(cfg));

        List<LlmConfigResponse> list = adminLlmConfigResource.listConfigs(null, "chat", null);

        assertEquals(1, list.size());
        assertEquals("m", list.get(0).getModelName());
    }

    @Test
    void listConfigs_withProviderIdFilter() {
        UUID providerId = UUID.randomUUID();
        LlmConfig cfg = new LlmConfig();
        cfg.setId(UUID.randomUUID());
        cfg.setModelName("m");
        LlmProvider p = new LlmProvider();
        p.setId(providerId);
        p.setName(ProviderName.OPENAI);
        cfg.setProvider(p);
        when(llmConfigRepository.list(anyString(), any(Object[].class))).thenReturn(List.of(cfg));

        List<LlmConfigResponse> list = adminLlmConfigResource.listConfigs(null, null, providerId);

        assertEquals(1, list.size());
        assertEquals("m", list.get(0).getModelName());
    }

    @Test
    void listConfigs_withAllFilters_queriesRepository() {
        UUID providerId = UUID.randomUUID();
        LlmConfig cfg = new LlmConfig();
        cfg.setId(UUID.randomUUID());
        cfg.setModelName("m");
        LlmProvider p = new LlmProvider();
        p.setId(providerId);
        p.setName(ProviderName.OPENAI);
        cfg.setProvider(p);
        when(llmConfigRepository.list(anyString(), any(Object[].class))).thenReturn(List.of(cfg));

        List<LlmConfigResponse> list =
                adminLlmConfigResource.listConfigs("tenant-1", "chat", providerId);

        assertEquals(1, list.size());
        verify(llmConfigRepository).list(anyString(), any(Object[].class));
    }

    @Test
    void createConfig_appliesDefaultQueueTimeoutAndPriorityWhenNull() {
        UUID providerId = UUID.randomUUID();
        LlmProvider provider = new LlmProvider();
        provider.setId(providerId);
        provider.setName(ProviderName.OPENAI);
        provider.setAuthType(AuthType.API_KEY);

        when(llmProviderRepository.findByIdOptional(providerId)).thenReturn(Optional.of(provider));
        when(encryptionService.encrypt("secret")).thenReturn("enc-blob");

        ValidationResultResponse ok = new ValidationResultResponse();
        ok.setValid(true);
        when(llmValidationService.validate(any(LlmConfig.class))).thenReturn(ok);

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(providerId);
        req.setModelName("gpt-4");
        req.setEndpointUrl("https://api.openai.com/v1/chat/completions");
        req.setCredentials("secret");
        req.setFeatures(List.of("chat"));
        req.setQueueTimeoutMs(null);
        req.setPriority(null);

        adminLlmConfigResource.createConfig(req);

        ArgumentCaptor<LlmConfig> cap = ArgumentCaptor.forClass(LlmConfig.class);
        verify(llmConfigRepository).persist(cap.capture());
        assertEquals(5000, cap.getValue().getQueueTimeoutMs());
        assertEquals(0, cap.getValue().getPriority());
    }

    @Test
    void createConfig_withIsFallbackTrue_setsFallbackFlag() {
        UUID providerId = UUID.randomUUID();
        LlmProvider provider = new LlmProvider();
        provider.setId(providerId);
        provider.setName(ProviderName.OPENAI);
        provider.setAuthType(AuthType.API_KEY);

        when(llmProviderRepository.findByIdOptional(providerId)).thenReturn(Optional.of(provider));
        when(encryptionService.encrypt("secret")).thenReturn("enc-blob");

        ValidationResultResponse ok = new ValidationResultResponse();
        ok.setValid(true);
        when(llmValidationService.validate(any(LlmConfig.class))).thenReturn(ok);

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(providerId);
        req.setModelName("gpt-4");
        req.setEndpointUrl("https://api.openai.com/v1/chat/completions");
        req.setCredentials("secret");
        req.setFeatures(List.of("chat"));
        req.setIsFallback(Boolean.TRUE);

        adminLlmConfigResource.createConfig(req);

        ArgumentCaptor<LlmConfig> cap = ArgumentCaptor.forClass(LlmConfig.class);
        verify(llmConfigRepository).persist(cap.capture());
        assertTrue(cap.getValue().isFallback());
    }

    @Test
    void updateConfig_whenPlaintextMatchesExisting_reusesEncryptedCredentials() {
        UUID id = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        LlmProvider provider = new LlmProvider();
        provider.setId(providerId);
        provider.setName(ProviderName.OPENAI);
        provider.setAuthType(AuthType.API_KEY);

        LlmConfig entity = new LlmConfig();
        entity.setId(id);
        entity.setCredentialsEncrypted("same-blob");
        when(llmConfigRepository.findByIdOptional(id)).thenReturn(Optional.of(entity));
        when(llmProviderRepository.findByIdOptional(providerId)).thenReturn(Optional.of(provider));
        when(encryptionService.decrypt("same-blob")).thenReturn("unchanged-secret");

        ValidationResultResponse ok = new ValidationResultResponse();
        ok.setValid(true);
        when(llmValidationService.validate(any(LlmConfig.class))).thenReturn(ok);

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(providerId);
        req.setModelName("gpt-4");
        req.setEndpointUrl("https://api.openai.com/v1/chat/completions");
        req.setCredentials("unchanged-secret");
        req.setFeatures(List.of("chat"));

        adminLlmConfigResource.updateConfig(id, req);

        verify(encryptionService).decrypt("same-blob");
        verify(encryptionService, never()).encrypt(anyString());
        assertEquals("same-blob", entity.getCredentialsEncrypted());
    }

    @Test
    void updateConfig_whenDecryptFails_reEncryptsCredentials() {
        UUID id = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        LlmProvider provider = new LlmProvider();
        provider.setId(providerId);
        provider.setName(ProviderName.OPENAI);
        provider.setAuthType(AuthType.API_KEY);

        LlmConfig entity = new LlmConfig();
        entity.setId(id);
        entity.setCredentialsEncrypted("bad-blob");
        when(llmConfigRepository.findByIdOptional(id)).thenReturn(Optional.of(entity));
        when(llmProviderRepository.findByIdOptional(providerId)).thenReturn(Optional.of(provider));
        when(encryptionService.decrypt("bad-blob"))
                .thenThrow(new EncryptionException("bad", new RuntimeException()));
        when(encryptionService.encrypt("new-secret")).thenReturn("new-blob");

        ValidationResultResponse ok = new ValidationResultResponse();
        ok.setValid(true);
        when(llmValidationService.validate(any(LlmConfig.class))).thenReturn(ok);

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(providerId);
        req.setModelName("gpt-4");
        req.setEndpointUrl("https://api.openai.com/v1/chat/completions");
        req.setCredentials("new-secret");
        req.setFeatures(List.of("chat"));

        adminLlmConfigResource.updateConfig(id, req);

        verify(encryptionService).encrypt("new-secret");
        assertEquals("new-blob", entity.getCredentialsEncrypted());
    }

    @Test
    void updateConfig_whenExistingEncryptedBlank_encryptsRequestCredentials() {
        UUID id = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        LlmProvider provider = new LlmProvider();
        provider.setId(providerId);
        provider.setName(ProviderName.OPENAI);
        provider.setAuthType(AuthType.API_KEY);

        LlmConfig entity = new LlmConfig();
        entity.setId(id);
        entity.setCredentialsEncrypted("   ");
        when(llmConfigRepository.findByIdOptional(id)).thenReturn(Optional.of(entity));
        when(llmProviderRepository.findByIdOptional(providerId)).thenReturn(Optional.of(provider));
        when(encryptionService.encrypt("secret")).thenReturn("fresh-blob");

        ValidationResultResponse ok = new ValidationResultResponse();
        ok.setValid(true);
        when(llmValidationService.validate(any(LlmConfig.class))).thenReturn(ok);

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(providerId);
        req.setModelName("gpt-4");
        req.setEndpointUrl("https://api.openai.com/v1/chat/completions");
        req.setCredentials("secret");
        req.setFeatures(List.of("chat"));

        adminLlmConfigResource.updateConfig(id, req);

        verify(encryptionService, never()).decrypt(anyString());
        verify(encryptionService).encrypt("secret");
        assertEquals("fresh-blob", entity.getCredentialsEncrypted());
    }

    @Test
    void createConfig_withNullFeatures_skipsAttachFeatures() {
        UUID providerId = UUID.randomUUID();
        LlmProvider provider = new LlmProvider();
        provider.setId(providerId);
        provider.setName(ProviderName.OPENAI);
        provider.setAuthType(AuthType.API_KEY);

        when(llmProviderRepository.findByIdOptional(providerId)).thenReturn(Optional.of(provider));
        when(encryptionService.encrypt("secret")).thenReturn("enc-blob");

        ValidationResultResponse ok = new ValidationResultResponse();
        ok.setValid(true);
        when(llmValidationService.validate(any(LlmConfig.class))).thenReturn(ok);

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(providerId);
        req.setModelName("gpt-4");
        req.setEndpointUrl("https://api.openai.com/v1/chat/completions");
        req.setCredentials("secret");
        req.setFeatures(null);

        Response response = adminLlmConfigResource.createConfig(req);

        assertEquals(201, response.getStatus());
        ArgumentCaptor<LlmConfig> cap = ArgumentCaptor.forClass(LlmConfig.class);
        verify(llmConfigRepository).persist(cap.capture());
        assertTrue(cap.getValue().getFeatures().isEmpty());
    }

    @Test
    void updateConfig_whenExistingEncryptedNull_encryptsRequestCredentials() {
        UUID id = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        LlmProvider provider = new LlmProvider();
        provider.setId(providerId);
        provider.setName(ProviderName.OPENAI);
        provider.setAuthType(AuthType.API_KEY);

        LlmConfig entity = new LlmConfig();
        entity.setId(id);
        entity.setCredentialsEncrypted(null);
        when(llmConfigRepository.findByIdOptional(id)).thenReturn(Optional.of(entity));
        when(llmProviderRepository.findByIdOptional(providerId)).thenReturn(Optional.of(provider));
        when(encryptionService.encrypt("secret")).thenReturn("fresh-blob");

        ValidationResultResponse ok = new ValidationResultResponse();
        ok.setValid(true);
        when(llmValidationService.validate(any(LlmConfig.class))).thenReturn(ok);

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(providerId);
        req.setModelName("gpt-4");
        req.setEndpointUrl("https://api.openai.com/v1/chat/completions");
        req.setCredentials("secret");
        req.setFeatures(List.of("chat"));

        Response response = adminLlmConfigResource.updateConfig(id, req);

        assertEquals(200, response.getStatus());
        verify(encryptionService, never()).decrypt(anyString());
        verify(encryptionService).encrypt("secret");
    }

    @Test
    void createConfig_withExplicitQueueTimeoutAndPriority() {
        UUID providerId = UUID.randomUUID();
        LlmProvider provider = new LlmProvider();
        provider.setId(providerId);
        provider.setName(ProviderName.OPENAI);
        provider.setAuthType(AuthType.API_KEY);

        when(llmProviderRepository.findByIdOptional(providerId)).thenReturn(Optional.of(provider));
        when(encryptionService.encrypt("secret")).thenReturn("enc-blob");

        ValidationResultResponse ok = new ValidationResultResponse();
        ok.setValid(true);
        when(llmValidationService.validate(any(LlmConfig.class))).thenReturn(ok);

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(providerId);
        req.setModelName("gpt-4");
        req.setEndpointUrl("https://api.openai.com/v1/chat/completions");
        req.setCredentials("secret");
        req.setFeatures(List.of("chat"));
        req.setQueueTimeoutMs(10000);
        req.setPriority(5);

        adminLlmConfigResource.createConfig(req);

        ArgumentCaptor<LlmConfig> cap = ArgumentCaptor.forClass(LlmConfig.class);
        verify(llmConfigRepository).persist(cap.capture());
        assertEquals(10000, cap.getValue().getQueueTimeoutMs());
        assertEquals(5, cap.getValue().getPriority());
    }

    @Test
    void updateConfig_credentialsChangedFromExisting_reEncrypts() {
        UUID id = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        LlmProvider provider = new LlmProvider();
        provider.setId(providerId);
        provider.setName(ProviderName.OPENAI);
        provider.setAuthType(AuthType.API_KEY);

        LlmConfig entity = new LlmConfig();
        entity.setId(id);
        entity.setCredentialsEncrypted("old-blob");
        when(llmConfigRepository.findByIdOptional(id)).thenReturn(Optional.of(entity));
        when(llmProviderRepository.findByIdOptional(providerId)).thenReturn(Optional.of(provider));
        when(encryptionService.decrypt("old-blob")).thenReturn("old-secret");
        when(encryptionService.encrypt("new-secret")).thenReturn("new-blob");

        ValidationResultResponse ok = new ValidationResultResponse();
        ok.setValid(true);
        when(llmValidationService.validate(any(LlmConfig.class))).thenReturn(ok);

        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(providerId);
        req.setModelName("gpt-4");
        req.setEndpointUrl("https://api.openai.com/v1/chat/completions");
        req.setCredentials("new-secret");
        req.setFeatures(List.of("chat"));

        adminLlmConfigResource.updateConfig(id, req);

        verify(encryptionService).encrypt("new-secret");
        assertEquals("new-blob", entity.getCredentialsEncrypted());
    }

    // =========================================================================
    // Gateway model identity — two configs of the same model must both persist.
    // =========================================================================

    @Test
    void createConfig_assignsGatewayModelNameBeforePersisting() {
        LlmProvider provider = provider();
        when(llmProviderRepository.findByIdOptional(any())).thenReturn(Optional.of(provider));
        when(encryptionService.encrypt(anyString())).thenReturn("enc");
        when(llmValidationService.validate(any())).thenReturn(validationOk());
        doAnswer(inv -> {
            LlmConfig e = inv.getArgument(0);
            e.setModelSlug("claude-sonnet-6");
            e.setModelSequence(1);
            e.setGatewayModelName("ai-bridge-1-claude-sonnet-6");
            return null;
        }).when(gatewayModelNameService).assign(any(), eq("claude-sonnet-6"));

        LlmConfigRequest req = request("claude-sonnet-6", provider.getId());
        Response response = adminLlmConfigResource.createConfig(req);

        assertEquals(201, response.getStatus());
        LlmConfigResponse body = (LlmConfigResponse) response.getEntity();
        assertEquals("ai-bridge-1-claude-sonnet-6", body.getGatewayModelName());

        ArgumentCaptor<LlmConfig> captor = ArgumentCaptor.forClass(LlmConfig.class);
        verify(llmConfigRepository).persist(captor.capture());
        assertEquals("ai-bridge-1-claude-sonnet-6", captor.getValue().getGatewayModelName());
    }

    @Test
    void createConfig_twoConfigsOfTheSameModelBothPersistWithDistinctGatewayNames() {
        LlmProvider provider = provider();
        when(llmProviderRepository.findByIdOptional(any())).thenReturn(Optional.of(provider));
        when(encryptionService.encrypt(anyString())).thenReturn("enc");
        when(llmValidationService.validate(any())).thenReturn(validationOk());

        AtomicInteger counter = new AtomicInteger();
        doAnswer(inv -> {
            LlmConfig e = inv.getArgument(0);
            int seq = counter.incrementAndGet();
            e.setModelSlug("claude-sonnet-6");
            e.setModelSequence(seq);
            e.setGatewayModelName("ai-bridge-" + seq + "-claude-sonnet-6");
            return null;
        }).when(gatewayModelNameService).assign(any(), eq("claude-sonnet-6"));

        Response first = adminLlmConfigResource.createConfig(request("claude-sonnet-6", provider.getId()));
        Response second = adminLlmConfigResource.createConfig(request("claude-sonnet-6", provider.getId()));

        assertEquals(201, first.getStatus());
        assertEquals(201, second.getStatus());
        assertEquals("ai-bridge-1-claude-sonnet-6",
                ((LlmConfigResponse) first.getEntity()).getGatewayModelName());
        assertEquals("ai-bridge-2-claude-sonnet-6",
                ((LlmConfigResponse) second.getEntity()).getGatewayModelName());
        verify(llmConfigRepository, times(2)).persist(any(LlmConfig.class));
    }

    @Test
    void createConfig_doesNotAssignAGatewayNameWhenValidationFails() {
        LlmProvider provider = provider();
        when(llmProviderRepository.findByIdOptional(any())).thenReturn(Optional.of(provider));
        when(encryptionService.encrypt(anyString())).thenReturn("enc");
        when(llmValidationService.validate(any()))
                .thenReturn(validationFailed("bad key"));

        Response response = adminLlmConfigResource.createConfig(request("claude-sonnet-6", provider.getId()));

        assertEquals(422, response.getStatus());
        // A rejected config must not burn a sequence number.
        verify(gatewayModelNameService, never()).assign(any(), anyString());
        verify(llmConfigRepository, never()).persist(any(LlmConfig.class));
    }

    @Test
    void updateConfig_delegatesToReassignSoUnchangedModelsKeepTheirName() {
        LlmProvider provider = provider();
        LlmConfig existing = new LlmConfig();
        existing.setId(UUID.randomUUID());
        existing.setProvider(provider);
        existing.setModelName("claude-sonnet-6");
        existing.setModelSlug("claude-sonnet-6");
        existing.setModelSequence(2);
        existing.setGatewayModelName("ai-bridge-2-claude-sonnet-6");
        existing.setCredentialsEncrypted("enc");

        when(llmConfigRepository.findByIdOptional(existing.getId())).thenReturn(Optional.of(existing));
        when(llmProviderRepository.findByIdOptional(any())).thenReturn(Optional.of(provider));
        when(encryptionService.decrypt("enc")).thenReturn("secret");
        when(llmValidationService.validate(any())).thenReturn(validationOk());

        LlmConfigRequest req = request("claude-sonnet-6", provider.getId());
        req.setCredentials("secret");

        Response response = adminLlmConfigResource.updateConfig(existing.getId(), req);

        assertEquals(200, response.getStatus());
        verify(gatewayModelNameService).reassignIfModelChanged(existing, "claude-sonnet-6");
        verify(gatewayModelNameService, never()).assign(any(), anyString());
    }

    private static ValidationResultResponse validationOk() {
        ValidationResultResponse r = new ValidationResultResponse();
        r.setValid(true);
        return r;
    }

    private static ValidationResultResponse validationFailed(String message) {
        ValidationResultResponse r = new ValidationResultResponse();
        r.setValid(false);
        r.setMessage(message);
        return r;
    }

    private static LlmProvider provider() {
        LlmProvider p = new LlmProvider();
        p.setId(UUID.randomUUID());
        p.setName(ProviderName.CLAUDE);
        p.setAuthType(AuthType.API_KEY);
        return p;
    }

    private static LlmConfigRequest request(String modelName, UUID providerId) {
        LlmConfigRequest req = new LlmConfigRequest();
        req.setProviderId(providerId);
        req.setModelName(modelName);
        req.setEndpointUrl("https://api.anthropic.com/v1/messages");
        req.setCredentials("sk-test");
        req.setFeatures(List.of("chat"));
        return req;
    }
}
