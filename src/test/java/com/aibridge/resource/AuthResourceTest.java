package com.aibridge.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibridge.config.AiBridgeConfig;
import com.aibridge.dto.auth.AuthRequest;
import com.aibridge.dto.auth.AuthResponse;
import com.aibridge.service.ApiAuthService;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class AuthResourceTest {

    @Mock
    ApiAuthService apiAuthService;

    @Mock
    AiBridgeConfig config;

    @InjectMocks
    AuthResource authResource;

    @Test
    void generateToken_withValidApiKey_returnsOkWithToken() {
        when(apiAuthService.generateToken("my-key")).thenReturn("tok-123");
        when(config.getAuthTokenValidityMinutes()).thenReturn(60);

        AuthRequest request = new AuthRequest("my-key");
        Response res = authResource.generateToken(request);

        assertEquals(200, res.getStatus());
        AuthResponse body = (AuthResponse) res.getEntity();
        assertEquals("tok-123", body.getToken());
        assertEquals(60, body.getExpiresInMinutes());
    }

    @Test
    void generateToken_withInvalidApiKey_returns401() {
        when(apiAuthService.generateToken("bad-key")).thenReturn(null);

        AuthRequest request = new AuthRequest("bad-key");
        Response res = authResource.generateToken(request);

        assertEquals(401, res.getStatus());
        Map<String, Object> body = (Map<String, Object>) res.getEntity();
        assertEquals("Invalid API key", body.get("error"));
    }

    @Test
    void generateToken_withNullRequest_returns400() {
        Response res = authResource.generateToken(null);

        assertEquals(400, res.getStatus());
        verify(apiAuthService, never()).generateToken(anyString());
    }

    @Test
    void generateToken_withNullApiKey_returns400() {
        AuthRequest request = new AuthRequest(null);
        Response res = authResource.generateToken(request);

        assertEquals(400, res.getStatus());
        verify(apiAuthService, never()).generateToken(anyString());
    }

    @Test
    void generateToken_withBlankApiKey_returns400() {
        AuthRequest request = new AuthRequest("   ");
        Response res = authResource.generateToken(request);

        assertEquals(400, res.getStatus());
        verify(apiAuthService, never()).generateToken(anyString());
    }
}
