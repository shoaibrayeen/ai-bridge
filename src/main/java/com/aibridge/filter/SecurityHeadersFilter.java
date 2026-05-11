package com.aibridge.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {

    private static final String HEADER_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String HEADER_FRAME_OPTIONS = "X-Frame-Options";
    private static final String HEADER_XSS_PROTECTION = "X-XSS-Protection";
    private static final String HEADER_REFERRER_POLICY = "Referrer-Policy";
    private static final String HEADER_CACHE_CONTROL = "Cache-Control";

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        var headers = responseContext.getHeaders();
        headers.putSingle(HEADER_CONTENT_TYPE_OPTIONS, "nosniff");
        headers.putSingle(HEADER_FRAME_OPTIONS, "DENY");
        headers.putSingle(HEADER_XSS_PROTECTION, "1; mode=block");
        headers.putSingle(HEADER_REFERRER_POLICY, "strict-origin-when-cross-origin");

        String path = requestContext.getUriInfo().getRequestUri().getPath();
        if (path == null) {
            path = "";
        }
        boolean apiOrAdmin = path.startsWith("/admin/api/") || path.startsWith("/v1/")
                || path.equals("/admin/api") || path.equals("/v1")
                || path.startsWith("admin/api/") || path.startsWith("v1/")
                || path.equals("admin/api") || path.equals("v1");
        if (apiOrAdmin) {
            headers.putSingle(HEADER_CACHE_CONTROL, "no-store");
        }
    }
}
