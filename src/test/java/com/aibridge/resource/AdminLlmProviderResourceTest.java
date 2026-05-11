package com.aibridge.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibridge.dto.admin.LlmProviderRequest;
import com.aibridge.dto.admin.LlmProviderResponse;
import com.aibridge.model.LlmProvider;
import com.aibridge.model.enums.AuthType;
import com.aibridge.model.enums.ProviderName;
import com.aibridge.repository.LlmProviderRepository;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminLlmProviderResourceTest {

    @Mock
    LlmProviderRepository llmProviderRepository;

    @InjectMocks
    AdminLlmProviderResource adminLlmProviderResource;

    @Test
    void listProviders_returnsAllRows() {
        LlmProvider p = new LlmProvider();
        p.setId(UUID.randomUUID());
        p.setName(ProviderName.OPENAI);
        p.setAuthType(AuthType.API_KEY);
        when(llmProviderRepository.listAll()).thenReturn(List.of(p));

        List<LlmProviderResponse> list = adminLlmProviderResource.listProviders();

        assertEquals(1, list.size());
        assertEquals("OPENAI", list.get(0).getName());
    }

    @Test
    void getProvider_missingId_returns404() {
        UUID id = UUID.randomUUID();
        when(llmProviderRepository.findByIdOptional(id)).thenReturn(Optional.empty());

        Response response = adminLlmProviderResource.getProvider(id);

        assertEquals(404, response.getStatus());
    }

    @Test
    void getProvider_existingId_returns200() {
        UUID id = UUID.randomUUID();
        LlmProvider p = new LlmProvider();
        p.setId(id);
        p.setName(ProviderName.CLAUDE);
        p.setAuthType(AuthType.API_KEY);
        when(llmProviderRepository.findByIdOptional(id)).thenReturn(Optional.of(p));

        Response response = adminLlmProviderResource.getProvider(id);

        assertEquals(200, response.getStatus());
        LlmProviderResponse body = (LlmProviderResponse) response.getEntity();
        assertEquals("CLAUDE", body.getName());
    }

    @Test
    void createProvider_persistsEntity() {
        LlmProviderRequest req = new LlmProviderRequest();
        req.setName("OPENAI");
        req.setAuthType("API_KEY");

        Response response = adminLlmProviderResource.createProvider(req);

        assertEquals(201, response.getStatus());
        verify(llmProviderRepository).persist(any(LlmProvider.class));
        assertNotNull(response.getEntity());
    }

    @Test
    void updateProvider_existing_returns200() {
        UUID id = UUID.randomUUID();
        LlmProvider p = new LlmProvider();
        p.setId(id);
        p.setName(ProviderName.OPENAI);
        p.setAuthType(AuthType.API_KEY);
        when(llmProviderRepository.findByIdOptional(id)).thenReturn(Optional.of(p));

        LlmProviderRequest req = new LlmProviderRequest();
        req.setName("CLAUDE");
        req.setAuthType("API_KEY");

        Response response = adminLlmProviderResource.updateProvider(id, req);

        assertEquals(200, response.getStatus());
        assertEquals("CLAUDE", p.getName().name());
    }

    @Test
    void updateProvider_missing_returns404() {
        UUID id = UUID.randomUUID();
        when(llmProviderRepository.findByIdOptional(id)).thenReturn(Optional.empty());
        LlmProviderRequest req = new LlmProviderRequest();
        req.setName("OPENAI");
        req.setAuthType("API_KEY");

        Response response = adminLlmProviderResource.updateProvider(id, req);

        assertEquals(404, response.getStatus());
    }

    @Test
    void deleteProvider_missing_returns404() {
        UUID id = UUID.randomUUID();
        when(llmProviderRepository.findByIdOptional(id)).thenReturn(Optional.empty());

        Response response = adminLlmProviderResource.deleteProvider(id);

        assertEquals(404, response.getStatus());
    }

    @Test
    void deleteProvider_existing_returns204() {
        UUID id = UUID.randomUUID();
        LlmProvider p = new LlmProvider();
        p.setId(id);
        when(llmProviderRepository.findByIdOptional(id)).thenReturn(Optional.of(p));

        Response response = adminLlmProviderResource.deleteProvider(id);

        assertEquals(204, response.getStatus());
        verify(llmProviderRepository).deleteById(id);
    }
}
