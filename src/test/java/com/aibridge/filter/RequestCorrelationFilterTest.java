package com.aibridge.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import java.lang.reflect.Field;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestCorrelationFilterTest {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);

    @Mock
    ContainerRequestContext requestContext;

    @Mock
    ContainerResponseContext responseContext;

    @InjectMocks
    RequestCorrelationFilter filter;

    @AfterEach
    void clearThreadLocal() throws Exception {
        Field f = RequestCorrelationFilter.class.getDeclaredField("REQUEST_ID");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        ThreadLocal<String> tl = (ThreadLocal<String>) f.get(null);
        tl.remove();
    }

    @Test
    void requestFilter_whenHeaderPresent_usesProvidedId() throws Exception {
        when(requestContext.getHeaderString(RequestCorrelationFilter.HEADER_REQUEST_ID)).thenReturn("req-abc-123");

        filter.filter(requestContext);

        assertEquals("req-abc-123", RequestCorrelationFilter.currentRequestId());
    }

    @Test
    void requestFilter_whenHeaderAbsent_generatesUuid() throws Exception {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        when(requestContext.getHeaderString(RequestCorrelationFilter.HEADER_REQUEST_ID)).thenReturn(null);
        when(requestContext.getHeaders()).thenReturn(headers);

        filter.filter(requestContext);

        String id = RequestCorrelationFilter.currentRequestId();
        assertNotNull(id);
        assertTrue(UUID_PATTERN.matcher(id).matches());
        assertEquals(id, headers.getFirst(RequestCorrelationFilter.HEADER_REQUEST_ID));
    }

    @Test
    void requestFilter_whenHeaderBlank_generatesUuid() throws Exception {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        when(requestContext.getHeaderString(RequestCorrelationFilter.HEADER_REQUEST_ID)).thenReturn("  \t ");
        when(requestContext.getHeaders()).thenReturn(headers);

        filter.filter(requestContext);

        String id = RequestCorrelationFilter.currentRequestId();
        assertNotNull(id);
        assertTrue(UUID_PATTERN.matcher(id).matches());
        assertEquals(id, headers.getFirst(RequestCorrelationFilter.HEADER_REQUEST_ID));
    }

    @Test
    void responseFilter_propagatesIdToResponse() throws Exception {
        when(requestContext.getHeaderString(RequestCorrelationFilter.HEADER_REQUEST_ID)).thenReturn("trace-xyz");

        filter.filter(requestContext);

        MultivaluedMap<String, Object> resHeaders = new MultivaluedHashMap<>();
        when(responseContext.getHeaders()).thenReturn(resHeaders);

        filter.filter(requestContext, responseContext);

        assertEquals("trace-xyz", resHeaders.getFirst(RequestCorrelationFilter.HEADER_REQUEST_ID));
        assertNull(RequestCorrelationFilter.currentRequestId());
    }

    @Test
    void responseFilter_cleansUpThreadLocal() throws Exception {
        when(requestContext.getHeaderString(RequestCorrelationFilter.HEADER_REQUEST_ID)).thenReturn("cleanup-me");
        MultivaluedMap<String, Object> resHeaders = new MultivaluedHashMap<>();
        when(responseContext.getHeaders()).thenReturn(resHeaders);

        filter.filter(requestContext);
        assertEquals("cleanup-me", RequestCorrelationFilter.currentRequestId());

        filter.filter(requestContext, responseContext);

        assertNull(RequestCorrelationFilter.currentRequestId());
    }

    @Test
    void responseFilter_whenNoRequestId_doesNotSetResponseHeader() {
        filter.filter(requestContext, responseContext);

        verify(responseContext, never()).getHeaders();
    }

    @Test
    void currentRequestId_reflectsLatestRequestFilter() throws Exception {
        when(requestContext.getHeaderString(RequestCorrelationFilter.HEADER_REQUEST_ID)).thenReturn("first");
        filter.filter(requestContext);
        assertEquals("first", RequestCorrelationFilter.currentRequestId());

        when(requestContext.getHeaderString(RequestCorrelationFilter.HEADER_REQUEST_ID)).thenReturn("second");
        filter.filter(requestContext);
        assertEquals("second", RequestCorrelationFilter.currentRequestId());
    }
}
