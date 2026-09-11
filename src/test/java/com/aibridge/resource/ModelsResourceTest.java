package com.aibridge.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.aibridge.dto.openai.ModelListResponse;
import com.aibridge.dto.openai.ModelObject;
import com.aibridge.model.Feature;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.LlmProvider;
import com.aibridge.model.enums.ProviderName;
import com.aibridge.repository.LlmConfigRepository;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelsResourceTest {

    @Mock
    LlmConfigRepository llmConfigRepository;

    @InjectMocks
    ModelsResource modelsResource;

    // ---------- listing ----------

    @Test
    void list_returnsOpenAiListEnvelope() {
        when(llmConfigRepository.listActiveVisibleTo("acme"))
                .thenReturn(List.of(config("acme", "claude-sonnet-6", 1, ProviderName.CLAUDE, "chat")));

        Response response = modelsResource.listModels("acme");

        assertEquals(200, response.getStatus());
        ModelListResponse body = (ModelListResponse) response.getEntity();
        assertEquals("list", body.getObject());
        assertEquals(1, body.getData().size());

        ModelObject model = body.getData().get(0);
        assertEquals("ai-bridge-1-claude-sonnet-6", model.getId());
        assertEquals("model", model.getObject());
        assertEquals("claude", model.getOwnedBy());
        assertEquals("claude-sonnet-6", model.getRoot());
        assertEquals(List.of("chat"), model.getFeatures());
    }

    @Test
    void list_exposesTwoConfigsOfTheSameModelUnderDistinctIds() {
        when(llmConfigRepository.listActiveVisibleTo("acme"))
                .thenReturn(List.of(
                        config("acme", "claude-sonnet-6", 1, ProviderName.CLAUDE, "chat"),
                        config("acme", "claude-sonnet-6", 2, ProviderName.CLAUDE, "summarization")));

        ModelListResponse body = (ModelListResponse) modelsResource.listModels("acme").getEntity();

        List<String> ids = body.getData().stream().map(ModelObject::getId).toList();
        assertEquals(List.of("ai-bridge-1-claude-sonnet-6", "ai-bridge-2-claude-sonnet-6"), ids);
        // Same underlying model, two addressable gateway identities.
        assertEquals("claude-sonnet-6", body.getData().get(0).getRoot());
        assertEquals("claude-sonnet-6", body.getData().get(1).getRoot());
    }

    @Test
    void list_withoutTenantHeader_scopesToGlobalConfigs() {
        when(llmConfigRepository.listActiveVisibleTo(null))
                .thenReturn(List.of(config(null, "gpt-4o", 1, ProviderName.OPENAI, "chat")));

        Response response = modelsResource.listModels(null);

        assertEquals(200, response.getStatus());
        ModelListResponse body = (ModelListResponse) response.getEntity();
        assertEquals("ai-bridge-1-gpt-4o", body.getData().get(0).getId());
    }

    @Test
    void list_blankTenantHeaderIsTreatedAsAbsent() {
        when(llmConfigRepository.listActiveVisibleTo(null)).thenReturn(List.of());

        assertEquals(200, modelsResource.listModels("   ").getStatus());
    }

    @Test
    void list_emptyResultStillReturnsWellFormedEnvelope() {
        when(llmConfigRepository.listActiveVisibleTo("acme")).thenReturn(List.of());

        ModelListResponse body = (ModelListResponse) modelsResource.listModels("acme").getEntity();

        assertEquals("list", body.getObject());
        assertTrue(body.getData().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad tenant!", "tenant/../etc", "a b", "drop;table"})
    void list_rejectsMalformedTenantHeader(String tenantId) {
        Response response = modelsResource.listModels(tenantId);

        assertEquals(400, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getEntity();
        assertEquals("Invalid X-Tenant-ID header", body.get("error"));
    }

    // ---------- single model ----------

    @Test
    void get_returnsTheModelWhenOwnedByTheTenant() {
        when(llmConfigRepository.findActiveByGatewayModelName("ai-bridge-1-gpt-4o"))
                .thenReturn(config("acme", "gpt-4o", 1, ProviderName.OPENAI, "chat"));

        Response response = modelsResource.getModel("acme", "ai-bridge-1-gpt-4o");

        assertEquals(200, response.getStatus());
        assertEquals("ai-bridge-1-gpt-4o", ((ModelObject) response.getEntity()).getId());
    }

    @Test
    void get_returnsGlobalModelForAnyTenant() {
        when(llmConfigRepository.findActiveByGatewayModelName("ai-bridge-1-gpt-4o"))
                .thenReturn(config(null, "gpt-4o", 1, ProviderName.OPENAI, "chat"));

        assertEquals(200, modelsResource.getModel("any-tenant", "ai-bridge-1-gpt-4o").getStatus());
    }

    @Test
    void get_anotherTenantsModelIsIndistinguishableFromMissing() {
        when(llmConfigRepository.findActiveByGatewayModelName("ai-bridge-1-gpt-4o"))
                .thenReturn(config("other-tenant", "gpt-4o", 1, ProviderName.OPENAI, "chat"));

        Response response = modelsResource.getModel("acme", "ai-bridge-1-gpt-4o");

        assertEquals(404, response.getStatus());
    }

    @Test
    void get_unknownModelReturns404() {
        when(llmConfigRepository.findActiveByGatewayModelName("ai-bridge-9-nope")).thenReturn(null);

        assertEquals(404, modelsResource.getModel("acme", "ai-bridge-9-nope").getStatus());
    }

    @Test
    void get_rejectsMalformedTenantHeader() {
        assertEquals(400, modelsResource.getModel("bad tenant!", "ai-bridge-1-gpt-4o").getStatus());
    }

    @Test
    void post_isNotAllowed() {
        assertEquals(405, modelsResource.unsupported("ai-bridge-1-gpt-4o").getStatus());
    }

    private static LlmConfig config(
            String tenantId, String modelName, int sequence, ProviderName provider, String feature) {
        LlmProvider p = new LlmProvider();
        p.setName(provider);

        LlmConfig c = new LlmConfig();
        c.setId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setProvider(p);
        c.setModelName(modelName);
        c.setModelSlug(modelName);
        c.setModelSequence(sequence);
        c.setGatewayModelName("ai-bridge-" + sequence + "-" + modelName);
        c.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        c.setActive(true);

        Feature f = new Feature();
        f.setFeature(feature);
        f.setLlmConfig(c);
        c.getFeatures().add(f);
        return c;
    }
}
