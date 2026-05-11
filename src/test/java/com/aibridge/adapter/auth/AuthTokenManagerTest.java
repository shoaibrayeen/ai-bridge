package com.aibridge.adapter.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibridge.cache.CacheProvider;
import com.aibridge.config.RedisConfig;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.LlmProvider;
import com.aibridge.model.enums.AuthType;
import com.aibridge.model.enums.ProviderName;
import com.aibridge.service.EncryptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class AuthTokenManagerTest {

    private static final int CACHE_TTL_SECONDS = 55 * 60;

    @Mock
    CacheProvider cacheProvider;

    @Mock
    EncryptionService encryptionService;

    @Mock
    HttpClient httpClient;

    @Mock
    HttpResponse<String> httpResponse;

    @Test
    void getToken_cacheHit_returnsCachedTokenWithoutExchange() throws Exception {
        LlmConfig config = iamConfig();
        String key = cacheKey(config);
        when(cacheProvider.get(key)).thenReturn("cached-access-token");

        AuthTokenManager manager = newManager(httpClient);
        wire(manager);

        assertEquals("cached-access-token", manager.getToken(config));

        verify(cacheProvider, never()).setex(anyString(), anyLong(), anyString());
        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void getToken_whenCachedEmptyString_exchangesAndCaches() throws Exception {
        LlmConfig config = iamConfig();
        String key = cacheKey(config);
        when(cacheProvider.get(key)).thenReturn("");
        when(encryptionService.decrypt("enc")).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"access_token\":\"t1\"}");
        stubSend(httpClient, httpResponse);

        AuthTokenManager manager = newManager(httpClient);
        wire(manager);

        assertEquals("t1", manager.getToken(config));
        verify(cacheProvider).setex(key, CACHE_TTL_SECONDS, "t1");
    }

    @Test
    void getToken_iamToken_exchangeBuildsFormAndReturnsAccessToken() throws Exception {
        LlmConfig config = iamConfig();
        String key = cacheKey(config);
        when(cacheProvider.get(key)).thenReturn(null);
        when(encryptionService.decrypt("enc")).thenReturn("{\"api_key\":\"ibm-key\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"access_token\":\"iam-tok\"}");
        stubSend(httpClient, httpResponse);

        AuthTokenManager manager = newManager(httpClient);
        wire(manager);

        assertEquals("iam-tok", manager.getToken(config));

        ArgumentCaptor<HttpRequest> reqCap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(reqCap.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest sent = reqCap.getValue();
        assertEquals(URI.create("https://iam.example/token"), sent.uri());
        assertEquals("POST", sent.method());
        assertEquals(
                "application/x-www-form-urlencoded",
                sent.headers().firstValue("Content-Type").orElse(""));
        String body = bodyToString(sent);
        assertTrue(body.contains("grant_type="));
        assertTrue(body.contains("urn%3Aibm%3Aparams%3Aoauth%3Agrant-type%3Aapikey"));
        assertTrue(body.contains("apikey="));
        assertTrue(body.contains("ibm-key"));

        verify(cacheProvider).setex(key, CACHE_TTL_SECONDS, "iam-tok");
    }

    @Test
    void getToken_oauth2_exchangeUsesClientCredentials() throws Exception {
        LlmConfig config = oauth2Config();
        String key = cacheKey(config);
        when(cacheProvider.get(key)).thenReturn(null);
        when(encryptionService.decrypt("enc"))
                .thenReturn("{\"client_id\":\"cid\",\"client_secret\":\"csec\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"access_token\":\"oauth-tok\"}");
        stubSend(httpClient, httpResponse);

        AuthTokenManager manager = newManager(httpClient);
        wire(manager);

        assertEquals("oauth-tok", manager.getToken(config));

        ArgumentCaptor<HttpRequest> reqCap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(reqCap.capture(), any(HttpResponse.BodyHandler.class));
        String body = bodyToString(reqCap.getValue());
        assertTrue(body.contains("grant_type=client_credentials"));
        assertTrue(body.contains("client_id=cid"));
        assertTrue(body.contains("client_secret=csec"));

        verify(cacheProvider).setex(key, CACHE_TTL_SECONDS, "oauth-tok");
    }

    @Test
    void getToken_unsupportedAuthType_throwsIllegalStateException() {
        LlmConfig config = apiKeyConfig();
        String key = cacheKey(config);
        when(cacheProvider.get(key)).thenReturn(null);
        when(encryptionService.decrypt("enc")).thenReturn("{\"api_key\":\"x\"}");

        AuthTokenManager manager = newManager(httpClient);
        wire(manager);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> manager.getToken(config));
        assertTrue(ex.getMessage().contains("Token exchange not supported"));
    }

    @Test
    void getToken_blankAuthEndpoint_throwsIllegalStateException() {
        LlmConfig config = iamConfig();
        config.getProvider().setAuthEndpoint("   ");
        String key = cacheKey(config);
        when(cacheProvider.get(key)).thenReturn(null);
        when(encryptionService.decrypt("enc")).thenReturn("{\"api_key\":\"k\"}");

        AuthTokenManager manager = newManager(httpClient);
        wire(manager);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> manager.getToken(config));
        assertTrue(ex.getMessage().contains("authEndpoint is required"));
    }

    @Test
    void getToken_nullAuthEndpoint_throwsIllegalStateException() {
        LlmConfig config = iamConfig();
        config.getProvider().setAuthEndpoint(null);
        String key = cacheKey(config);
        when(cacheProvider.get(key)).thenReturn(null);
        when(encryptionService.decrypt("enc")).thenReturn("{\"api_key\":\"k\"}");

        AuthTokenManager manager = newManager(httpClient);
        wire(manager);

        assertThrows(IllegalStateException.class, () -> manager.getToken(config));
    }

    @Test
    void getToken_invalidCredentialsJson_throwsIllegalStateException() {
        LlmConfig config = iamConfig();
        String key = cacheKey(config);
        when(cacheProvider.get(key)).thenReturn(null);
        when(encryptionService.decrypt("enc")).thenReturn("not-json");

        AuthTokenManager manager = newManager(httpClient);
        wire(manager);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> manager.getToken(config));
        assertTrue(ex.getMessage().contains("Invalid credentials JSON"));
    }

    @Test
    void getToken_httpErrorStatus_throwsIllegalStateException() throws Exception {
        LlmConfig config = iamConfig();
        String key = cacheKey(config);
        when(cacheProvider.get(key)).thenReturn(null);
        when(encryptionService.decrypt("enc")).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(401);
        stubSend(httpClient, httpResponse);

        AuthTokenManager manager = newManager(httpClient);
        wire(manager);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> manager.getToken(config));
        assertTrue(ex.getMessage().contains("Token exchange failed: HTTP 401"));
    }

    @Test
    void getToken_iamMissingApiKey_throwsIllegalStateException() {
        LlmConfig config = iamConfig();
        String key = cacheKey(config);
        when(cacheProvider.get(key)).thenReturn(null);
        when(encryptionService.decrypt("enc")).thenReturn("{}");

        AuthTokenManager manager = newManager(httpClient);
        wire(manager);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> manager.getToken(config));
        assertTrue(ex.getMessage().contains("Missing credentials field: api_key"));
    }

    @Test
    void getToken_responseEmptyAccessToken_throwsIllegalStateException() throws Exception {
        LlmConfig config = iamConfig();
        String key = cacheKey(config);
        when(cacheProvider.get(key)).thenReturn(null);
        when(encryptionService.decrypt("enc")).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"access_token\":\"\"}");
        stubSend(httpClient, httpResponse);

        AuthTokenManager manager = newManager(httpClient);
        wire(manager);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> manager.getToken(config));
        assertTrue(ex.getMessage().contains("missing access_token"));
    }

    @Test
    void getToken_responseMissingAccessToken_throwsIllegalStateException() throws Exception {
        LlmConfig config = iamConfig();
        String key = cacheKey(config);
        when(cacheProvider.get(key)).thenReturn(null);
        when(encryptionService.decrypt("enc")).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{}");
        stubSend(httpClient, httpResponse);

        AuthTokenManager manager = newManager(httpClient);
        wire(manager);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> manager.getToken(config));
        assertTrue(ex.getMessage().contains("missing access_token"));
    }

    @Test
    void getToken_responseAccessTokenJsonNull_throwsIllegalStateException() throws Exception {
        LlmConfig config = iamConfig();
        String key = cacheKey(config);
        when(cacheProvider.get(key)).thenReturn(null);
        when(encryptionService.decrypt("enc")).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"access_token\":null}");
        stubSend(httpClient, httpResponse);

        AuthTokenManager manager = newManager(httpClient);
        wire(manager);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> manager.getToken(config));
        assertTrue(ex.getMessage().contains("missing access_token"));
    }

    @Test
    void getToken_httpStatusBelow200_throwsIllegalStateException() throws Exception {
        LlmConfig config = iamConfig();
        String key = cacheKey(config);
        when(cacheProvider.get(key)).thenReturn(null);
        when(encryptionService.decrypt("enc")).thenReturn("{\"api_key\":\"k\"}");
        when(httpResponse.statusCode()).thenReturn(199);
        stubSend(httpClient, httpResponse);

        AuthTokenManager manager = newManager(httpClient);
        wire(manager);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> manager.getToken(config));
        assertTrue(ex.getMessage().contains("Token exchange failed: HTTP 199"));
    }

    @Test
    void getToken_oauth2MissingClientSecret_throwsIllegalStateException() {
        LlmConfig config = oauth2Config();
        String key = cacheKey(config);
        when(cacheProvider.get(key)).thenReturn(null);
        when(encryptionService.decrypt("enc")).thenReturn("{\"client_id\":\"cid\"}");

        AuthTokenManager manager = newManager(httpClient);
        wire(manager);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> manager.getToken(config));
        assertTrue(ex.getMessage().contains("Missing credentials field: client_secret"));
    }

    @Test
    void getToken_oauth2BlankClientId_throwsIllegalStateException() {
        LlmConfig config = oauth2Config();
        String key = cacheKey(config);
        when(cacheProvider.get(key)).thenReturn(null);
        when(encryptionService.decrypt("enc"))
                .thenReturn("{\"client_id\":\"  \",\"client_secret\":\"sec\"}");

        AuthTokenManager manager = newManager(httpClient);
        wire(manager);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> manager.getToken(config));
        assertTrue(ex.getMessage().contains("Missing credentials field: client_id"));
    }

    @Test
    void getToken_sendIOException_wrapsIllegalStateException() throws Exception {
        LlmConfig config = iamConfig();
        String key = cacheKey(config);
        when(cacheProvider.get(key)).thenReturn(null);
        when(encryptionService.decrypt("enc")).thenReturn("{\"api_key\":\"k\"}");
        stubSendThrows(httpClient, new java.io.IOException("network"));

        AuthTokenManager manager = newManager(httpClient);
        wire(manager);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> manager.getToken(config));
        assertTrue(ex.getMessage().contains("Token exchange request failed"));
    }

    @Test
    void getToken_sendInterrupted_restoresInterruptAndThrows() throws Exception {
        LlmConfig config = iamConfig();
        String key = cacheKey(config);
        when(cacheProvider.get(key)).thenReturn(null);
        when(encryptionService.decrypt("enc")).thenReturn("{\"api_key\":\"k\"}");
        stubSendThrows(httpClient, new InterruptedException("stopped"));

        AuthTokenManager manager = newManager(httpClient);
        wire(manager);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> manager.getToken(config));
        assertTrue(ex.getMessage().contains("Token exchange interrupted"));
        assertTrue(Thread.interrupted());
    }

    private void wire(AuthTokenManager manager) {
        manager.cacheProvider = cacheProvider;
        manager.encryptionService = encryptionService;
        manager.objectMapper = new ObjectMapper();
    }

    private static String cacheKey(LlmConfig config) {
        return RedisConfig.authTokenKey(
                config.getProvider().getId().toString(), config.getId().toString());
    }

    private static AuthTokenManager newManager(HttpClient client) {
        HttpClient.Builder builder = mock(HttpClient.Builder.class);
        when(builder.connectTimeout(any())).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        try (MockedStatic<HttpClient> hs = mockStatic(HttpClient.class)) {
            hs.when(HttpClient::newBuilder).thenReturn(builder);
            return new AuthTokenManager();
        }
    }

    @SuppressWarnings("unchecked")
    private static void stubSend(HttpClient client, HttpResponse<String> response) {
        try {
            doReturn(response)
                    .when(client)
                    .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void stubSendThrows(HttpClient client, Throwable t) {
        try {
            doThrow(t)
                    .when(client)
                    .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String bodyToString(HttpRequest request) {
        return request.bodyPublisher()
                .map(
                        pub -> {
                            var sub = new Object() {
                                String s = "";
                            };
                            pub.subscribe(
                                    new java.util.concurrent.Flow.Subscriber<>() {
                                        @Override
                                        public void onSubscribe(
                                                java.util.concurrent.Flow.Subscription subscription) {
                                            subscription.request(Long.MAX_VALUE);
                                        }

                                        @Override
                                        public void onNext(java.nio.ByteBuffer item) {
                                            byte[] arr = new byte[item.remaining()];
                                            item.get(arr);
                                            sub.s += new String(arr, java.nio.charset.StandardCharsets.UTF_8);
                                        }

                                        @Override
                                        public void onError(Throwable throwable) {}

                                        @Override
                                        public void onComplete() {}
                                    });
                            return sub.s;
                        })
                .orElse("");
    }

    private static LlmConfig iamConfig() {
        UUID pid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID cid = UUID.fromString("22222222-2222-2222-2222-222222222222");
        LlmProvider p = new LlmProvider();
        p.setId(pid);
        p.setName(ProviderName.OPENAI);
        p.setAuthType(AuthType.IAM_TOKEN);
        p.setAuthEndpoint("https://iam.example/token");
        LlmConfig c = new LlmConfig();
        c.setId(cid);
        c.setProvider(p);
        c.setCredentialsEncrypted("enc");
        return c;
    }

    private static LlmConfig oauth2Config() {
        UUID pid = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID cid = UUID.fromString("44444444-4444-4444-4444-444444444444");
        LlmProvider p = new LlmProvider();
        p.setId(pid);
        p.setName(ProviderName.OPENAI);
        p.setAuthType(AuthType.OAUTH2);
        p.setAuthEndpoint("https://oauth.example/token");
        LlmConfig c = new LlmConfig();
        c.setId(cid);
        c.setProvider(p);
        c.setCredentialsEncrypted("enc");
        return c;
    }

    private static LlmConfig apiKeyConfig() {
        LlmConfig c = iamConfig();
        c.getProvider().setAuthType(AuthType.API_KEY);
        return c;
    }

    @Test
    void formEncode_oddNumberOfArgs_throwsIllegalArgumentException() throws Exception {
        java.lang.reflect.Method m = AuthTokenManager.class.getDeclaredMethod("formEncode", String[].class);
        m.setAccessible(true);
        try {
            m.invoke(null, (Object) new String[]{"key"});
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
            assertEquals("Pairs required", e.getCause().getMessage());
            return;
        }
        throw new AssertionError("Expected InvocationTargetException");
    }
}
