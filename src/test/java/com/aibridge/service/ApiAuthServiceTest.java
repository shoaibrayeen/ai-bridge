package com.aibridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.startsWith;

import com.aibridge.cache.CacheProvider;
import com.aibridge.config.AiBridgeConfig;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiAuthServiceTest {

    @Mock
    CacheProvider cacheProvider;

    @Mock
    AiBridgeConfig config;

    @InjectMocks
    ApiAuthService apiAuthService;

    @BeforeEach
    void setUp() {
        when(config.getAuthApiKey()).thenReturn("test-api-key");
        when(config.getAuthTokenValidityMinutes()).thenReturn(60);
    }

    @Test
    void generateToken_withValidApiKey_returnsTokenAndCaches() {
        String token = apiAuthService.generateToken("test-api-key");

        assertNotNull(token);
        assertFalse(token.isBlank());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(cacheProvider).setex(keyCaptor.capture(), eq(3600L), eq("valid"));
        assertTrue(keyCaptor.getValue().startsWith("aibridge:api_token:"));
    }

    @Test
    void generateToken_withInvalidApiKey_returnsNull() {
        String token = apiAuthService.generateToken("wrong-key");

        assertNull(token);
        verify(cacheProvider, never()).setex(anyString(), anyLong(), anyString());
    }

    @Test
    void generateToken_withNullApiKey_returnsNull() {
        assertNull(apiAuthService.generateToken(null));
        verify(cacheProvider, never()).setex(anyString(), anyLong(), anyString());
    }

    @Test
    void validateToken_withValidToken_returnsTrue() {
        when(cacheProvider.get("aibridge:api_token:abc-123")).thenReturn("valid");

        assertTrue(apiAuthService.validateToken("abc-123"));
    }

    @Test
    void validateToken_withMissingToken_returnsFalse() {
        when(cacheProvider.get("aibridge:api_token:unknown")).thenReturn(null);

        assertFalse(apiAuthService.validateToken("unknown"));
    }

    @Test
    void validateToken_withExpiredToken_returnsFalse() {
        when(cacheProvider.get("aibridge:api_token:expired")).thenReturn(null);

        assertFalse(apiAuthService.validateToken("expired"));
    }

    @Test
    void validateToken_withNull_returnsFalse() {
        assertFalse(apiAuthService.validateToken(null));
    }

    @Test
    void validateToken_withBlank_returnsFalse() {
        assertFalse(apiAuthService.validateToken("   "));
    }

    @Test
    void generateToken_respectsConfiguredTtl() {
        when(config.getAuthTokenValidityMinutes()).thenReturn(30);

        apiAuthService.generateToken("test-api-key");

        verify(cacheProvider).setex(anyString(), eq(1800L), eq("valid"));
    }

    @Test
    void validateToken_withWrongCacheValue_returnsFalse() {
        when(cacheProvider.get("aibridge:api_token:tok")).thenReturn("invalid-value");

        assertFalse(apiAuthService.validateToken("tok"));
    }

    // =========================================================================
    // Security: constant-time comparison and brute-force limiting.
    // =========================================================================

    @Test
    void constantTimeEquals_matchesOnlyIdenticalSecrets() {
        assertTrue(ApiAuthService.constantTimeEquals("secret", "secret"));
        assertFalse(ApiAuthService.constantTimeEquals("secret", "secreu"));
        assertFalse(ApiAuthService.constantTimeEquals("secret", "secret-longer"));
        assertFalse(ApiAuthService.constantTimeEquals("", "secret"));
    }

    @Test
    void constantTimeEquals_nullIsNeverEqual() {
        assertFalse(ApiAuthService.constantTimeEquals(null, "secret"));
        assertFalse(ApiAuthService.constantTimeEquals("secret", null));
        assertFalse(ApiAuthService.constantTimeEquals(null, null));
    }

    @Test
    void generateToken_clearsTheFailureCounterOnSuccess() {
        when(config.getAuthApiKey()).thenReturn("right-key");
        when(config.getAuthTokenValidityMinutes()).thenReturn(60);
        when(cacheProvider.get("aibridge:auth_fail:1.2.3.4")).thenReturn("3");

        String token = apiAuthService.generateToken("right-key", "1.2.3.4");

        assertNotNull(token);
        verify(cacheProvider).del("aibridge:auth_fail:1.2.3.4");
    }

    @Test
    void generateToken_countsFailuresAgainstTheCaller() {
        when(config.getAuthApiKey()).thenReturn("right-key");
        when(cacheProvider.get("aibridge:auth_fail:1.2.3.4")).thenReturn(null);

        assertNull(apiAuthService.generateToken("wrong-key", "1.2.3.4"));

        verify(cacheProvider).setex("aibridge:auth_fail:1.2.3.4", 300L, "1");
    }

    @Test
    void generateToken_incrementsAnExistingFailureCount() {
        when(config.getAuthApiKey()).thenReturn("right-key");
        when(cacheProvider.get("aibridge:auth_fail:1.2.3.4")).thenReturn("4");

        assertNull(apiAuthService.generateToken("wrong-key", "1.2.3.4"));

        verify(cacheProvider).setex("aibridge:auth_fail:1.2.3.4", 300L, "5");
    }

    @Test
    void generateToken_lockedOutCallerIsRefusedWithoutComparingTheKey() {
        when(cacheProvider.get("aibridge:auth_fail:1.2.3.4")).thenReturn("10");

        assertNull(apiAuthService.generateToken("right-key", "1.2.3.4"));

        // The correct key must not get through, and the key is never even read.
        verify(config, never()).getAuthApiKey();
        verify(cacheProvider, never()).setex(startsWith("aibridge:api_token:"), anyLong(), anyString());
    }

    @Test
    void generateToken_corruptFailureCounterFailsClosed() {
        when(cacheProvider.get("aibridge:auth_fail:1.2.3.4")).thenReturn("not-a-number");

        assertNull(apiAuthService.generateToken("right-key", "1.2.3.4"));

        verify(config, never()).getAuthApiKey();
    }

    @Test
    void generateToken_withoutACallerIdSkipsFailureCounting() {
        when(config.getAuthApiKey()).thenReturn("right-key");
        when(config.getAuthTokenValidityMinutes()).thenReturn(60);

        assertNotNull(apiAuthService.generateToken("right-key", null));

        verify(cacheProvider, never()).get(startsWith("aibridge:auth_fail:"));
    }
}
