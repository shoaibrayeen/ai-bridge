package com.aibridge.filter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibridge.service.ApiAuthService;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class ApiAuthFilterTest {

    @Mock
    ApiAuthService apiAuthService;

    @Mock
    ContainerRequestContext ctx;

    @Mock
    UriInfo uriInfo;

    private ApiAuthFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        filter = new ApiAuthFilter();
        Field f = ApiAuthFilter.class.getDeclaredField("apiAuthService");
        f.setAccessible(true);
        f.set(filter, apiAuthService);
    }

    @Test
    void filter_authTokenPath_doesNotCheck() {
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/api/auth/token");

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }

    @Test
    void filter_staticResourcePath_doesNotCheck() {
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/index.html");

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }

    @Test
    void filter_optionsPreflight_allowed() {
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/v1/chat/completions");
        when(ctx.getMethod()).thenReturn("OPTIONS");

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }

    @Test
    void filter_v1Path_noAuthHeader_returns401() {
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/v1/chat/completions");
        when(ctx.getMethod()).thenReturn("POST");
        when(ctx.getHeaderString("Authorization")).thenReturn(null);

        filter.filter(ctx);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(ctx).abortWith(captor.capture());
        assertEquals(401, captor.getValue().getStatus());
    }

    @Test
    void filter_v1ExactPath_noAuthHeader_returns401() {
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/v1");
        when(ctx.getMethod()).thenReturn("GET");
        when(ctx.getHeaderString("Authorization")).thenReturn(null);

        filter.filter(ctx);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(ctx).abortWith(captor.capture());
        assertEquals(401, captor.getValue().getStatus());
    }

    @Test
    void filter_adminApiPath_noAuthHeader_returns401() {
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/admin/api/llm-configs");
        when(ctx.getMethod()).thenReturn("GET");
        when(ctx.getHeaderString("Authorization")).thenReturn(null);

        filter.filter(ctx);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(ctx).abortWith(captor.capture());
        assertEquals(401, captor.getValue().getStatus());
    }

    @Test
    void filter_adminApiExactPath_noAuthHeader_returns401() {
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/admin/api");
        when(ctx.getMethod()).thenReturn("GET");
        when(ctx.getHeaderString("Authorization")).thenReturn(null);

        filter.filter(ctx);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(ctx).abortWith(captor.capture());
        assertEquals(401, captor.getValue().getStatus());
    }

    @Test
    void filter_invalidBearerPrefix_returns401() {
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/v1/chat/completions");
        when(ctx.getMethod()).thenReturn("POST");
        when(ctx.getHeaderString("Authorization")).thenReturn("Basic abc");

        filter.filter(ctx);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(ctx).abortWith(captor.capture());
        assertEquals(401, captor.getValue().getStatus());
    }

    @Test
    void filter_invalidToken_returns401() {
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/v1/chat/completions");
        when(ctx.getMethod()).thenReturn("POST");
        when(ctx.getHeaderString("Authorization")).thenReturn("Bearer bad-token");
        when(apiAuthService.validateToken("bad-token")).thenReturn(false);

        filter.filter(ctx);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(ctx).abortWith(captor.capture());
        assertEquals(401, captor.getValue().getStatus());
    }

    @Test
    void filter_validToken_v1_allows() {
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/v1/chat/completions");
        when(ctx.getMethod()).thenReturn("POST");
        when(ctx.getHeaderString("Authorization")).thenReturn("Bearer good-token");
        when(apiAuthService.validateToken("good-token")).thenReturn(true);

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }

    @Test
    void filter_validToken_adminApi_allows() {
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/admin/api/health");
        when(ctx.getMethod()).thenReturn("GET");
        when(ctx.getHeaderString("Authorization")).thenReturn("Bearer good-token");
        when(apiAuthService.validateToken("good-token")).thenReturn(true);

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }

    @Test
    void filter_validTokenCaseInsensitiveBearer_allows() {
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/admin/api/health");
        when(ctx.getMethod()).thenReturn("GET");
        when(ctx.getHeaderString("Authorization")).thenReturn("BEARER my-token");
        when(apiAuthService.validateToken("my-token")).thenReturn(true);

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }

    @Test
    void filter_pathWithoutLeadingSlash_stillChecked() {
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("v1/chat/completions");
        when(ctx.getMethod()).thenReturn("POST");
        when(ctx.getHeaderString("Authorization")).thenReturn(null);

        filter.filter(ctx);

        verify(ctx).abortWith(any());
    }

    @Test
    void filter_adminApiOptionsPreflightAllowed() {
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("/admin/api/llm-configs");
        when(ctx.getMethod()).thenReturn("OPTIONS");

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }
}
