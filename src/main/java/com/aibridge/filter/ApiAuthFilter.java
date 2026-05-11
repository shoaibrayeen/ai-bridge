package com.aibridge.filter;

import com.aibridge.service.ApiAuthService;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Protects {@code /v1/*} and {@code /admin/api/*} endpoints with bearer-token
 * authentication. The auth endpoint ({@code /api/auth/token}) is excluded.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class ApiAuthFilter implements ContainerRequestFilter {

    @Inject
    ApiAuthService apiAuthService;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (!requiresAuth(path)) {
            return;
        }
        if ("OPTIONS".equalsIgnoreCase(ctx.getMethod())) {
            return;
        }
        String authHeader = ctx.getHeaderString("Authorization");
        if (authHeader == null || !authHeader.regionMatches(true, 0, "bearer ", 0, 7)) {
            ctx.abortWith(unauthorized());
            return;
        }
        String token = authHeader.substring(7).trim();
        if (!apiAuthService.validateToken(token)) {
            ctx.abortWith(unauthorized());
        }
    }

    private static boolean requiresAuth(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        return p.startsWith("v1/") || p.equals("v1")
                || p.startsWith("admin/api/") || p.equals("admin/api");
    }

    private static Response unauthorized() {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("type", "Unauthorized");
        err.put("message", "Missing or invalid authentication token");
        err.put("status", 401);
        return Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", err))
                .build();
    }
}
