package com.aibridge.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SecurityHeadersFilterTest {

    @Mock
    ContainerRequestContext requestContext;

    @Mock
    ContainerResponseContext responseContext;

    @Mock
    UriInfo uriInfo;

    @InjectMocks
    SecurityHeadersFilter filter;

    private MultivaluedMap<String, Object> responseHeaders;

    @BeforeEach
    void stubHeaders() {
        responseHeaders = new MultivaluedHashMap<>();
        when(responseContext.getHeaders()).thenReturn(responseHeaders);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
    }

    @Test
    void filter_setsSecurityHeaders() {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://host/"));

        filter.filter(requestContext, responseContext);

        assertEquals("nosniff", responseHeaders.getFirst("X-Content-Type-Options"));
        assertEquals("DENY", responseHeaders.getFirst("X-Frame-Options"));
        assertEquals("1; mode=block", responseHeaders.getFirst("X-XSS-Protection"));
        assertEquals("strict-origin-when-cross-origin", responseHeaders.getFirst("Referrer-Policy"));
    }

    @Test
    void filter_adminApiPath_addsNoStoreCacheControl() {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://host/admin/api/foo"));

        filter.filter(requestContext, responseContext);

        assertEquals("no-store", responseHeaders.getFirst("Cache-Control"));
    }

    @Test
    void filter_v1Path_addsNoStoreCacheControl() {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://host/v1/chat"));

        filter.filter(requestContext, responseContext);

        assertEquals("no-store", responseHeaders.getFirst("Cache-Control"));
    }

    @Test
    void filter_nonApiPath_doesNotAddCacheControl() {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://host/"));

        filter.filter(requestContext, responseContext);

        assertNull(responseHeaders.getFirst("Cache-Control"));
    }

    @Test
    void filter_nullPath_doesNotAddCacheControl() {
        URI uriWithNullPath = mock(URI.class);
        when(uriWithNullPath.getPath()).thenReturn(null);
        when(uriInfo.getRequestUri()).thenReturn(uriWithNullPath);

        filter.filter(requestContext, responseContext);

        assertNull(responseHeaders.getFirst("Cache-Control"));
    }

    @Test
    void filter_adminApiExact_addsNoStore() {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://host/admin/api"));

        filter.filter(requestContext, responseContext);

        assertEquals("no-store", responseHeaders.getFirst("Cache-Control"));
    }

    @Test
    void filter_v1Exact_addsNoStore() {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("https://host/v1"));

        filter.filter(requestContext, responseContext);

        assertEquals("no-store", responseHeaders.getFirst("Cache-Control"));
    }

    @Test
    void filter_pathWithoutLeadingSlash_adminApi_addsNoStore() {
        URI uri = mock(URI.class);
        when(uri.getPath()).thenReturn("admin/api/foo");
        when(uriInfo.getRequestUri()).thenReturn(uri);

        filter.filter(requestContext, responseContext);

        assertEquals("no-store", responseHeaders.getFirst("Cache-Control"));
    }

    @Test
    void filter_relativeStylePath_adminApiPrefix_addsNoStore() {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("admin/api/roles"));

        filter.filter(requestContext, responseContext);

        assertEquals("no-store", responseHeaders.getFirst("Cache-Control"));
    }

    @Test
    void filter_relativeStylePath_v1Prefix_addsNoStore() {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("v1/models"));

        filter.filter(requestContext, responseContext);

        assertEquals("no-store", responseHeaders.getFirst("Cache-Control"));
    }

    @Test
    void filter_relativeStyle_adminApiExact_addsNoStore() {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("admin/api"));

        filter.filter(requestContext, responseContext);

        assertEquals("no-store", responseHeaders.getFirst("Cache-Control"));
    }

    @Test
    void filter_relativeStyle_v1Exact_addsNoStore() {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("v1"));

        filter.filter(requestContext, responseContext);

        assertEquals("no-store", responseHeaders.getFirst("Cache-Control"));
    }
}
