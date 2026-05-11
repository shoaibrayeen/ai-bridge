package com.aibridge.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.UUID;

@Provider
public class RequestCorrelationFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String HEADER_REQUEST_ID = "X-Request-ID";

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    /**
     * Request id for the current thread, if {@link RequestCorrelationFilter} has run.
     */
    public static String currentRequestId() {
        return REQUEST_ID.get();
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String id = requestContext.getHeaderString(HEADER_REQUEST_ID);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
            requestContext.getHeaders().putSingle(HEADER_REQUEST_ID, id);
        }
        REQUEST_ID.set(id);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        try {
            String id = REQUEST_ID.get();
            if (id != null) {
                responseContext.getHeaders().putSingle(HEADER_REQUEST_ID, id);
            }
        } finally {
            REQUEST_ID.remove();
        }
    }
}
