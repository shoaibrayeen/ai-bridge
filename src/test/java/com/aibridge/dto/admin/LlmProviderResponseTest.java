package com.aibridge.dto.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.aibridge.model.LlmProvider;
import com.aibridge.model.enums.AuthType;
import com.aibridge.model.enums.ProviderName;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LlmProviderResponseTest {

    @Test
    void from_mapsAllFields() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2024-03-01T10:00:00Z");
        Instant updated = Instant.parse("2024-03-02T10:00:00Z");

        LlmProvider entity = new LlmProvider();
        entity.setId(id);
        entity.setName(ProviderName.CLAUDE);
        entity.setAuthType(AuthType.OAUTH2);
        entity.setAuthEndpoint("https://auth.example/token");
        entity.setCreatedAt(created);
        entity.setUpdatedAt(updated);

        LlmProviderResponse r = LlmProviderResponse.from(entity);

        assertEquals(id, r.getId());
        assertEquals(ProviderName.CLAUDE.name(), r.getName());
        assertEquals(AuthType.OAUTH2.name(), r.getAuthType());
        assertEquals("https://auth.example/token", r.getAuthEndpoint());
        assertEquals(created, r.getCreatedAt());
        assertEquals(updated, r.getUpdatedAt());
    }

    @Test
    void from_nullNameAndAuthType_mapsNullStrings() {
        LlmProvider entity = new LlmProvider();
        entity.setId(UUID.randomUUID());
        entity.setName(null);
        entity.setAuthType(null);
        entity.setAuthEndpoint(null);
        entity.setCreatedAt(null);
        entity.setUpdatedAt(null);

        LlmProviderResponse r = LlmProviderResponse.from(entity);

        assertNull(r.getName());
        assertNull(r.getAuthType());
        assertNull(r.getAuthEndpoint());
        assertNull(r.getCreatedAt());
        assertNull(r.getUpdatedAt());
    }

    @Test
    void gettersAndSetters_roundTrip() {
        LlmProviderResponse r = new LlmProviderResponse();
        UUID id = UUID.randomUUID();
        Instant t = Instant.now();

        r.setId(id);
        r.setName("OPENAI");
        r.setAuthType("API_KEY");
        r.setAuthEndpoint("ep");
        r.setCreatedAt(t);
        r.setUpdatedAt(t);

        assertEquals(id, r.getId());
        assertEquals("OPENAI", r.getName());
        assertEquals("API_KEY", r.getAuthType());
        assertEquals("ep", r.getAuthEndpoint());
        assertEquals(t, r.getCreatedAt());
        assertEquals(t, r.getUpdatedAt());
    }
}
