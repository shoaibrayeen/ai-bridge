package com.aibridge.resource;

import com.aibridge.config.AiBridgeConfig;
import com.aibridge.dto.auth.AuthRequest;
import com.aibridge.dto.auth.AuthResponse;
import com.aibridge.service.ApiAuthService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.Map;

@ApplicationScoped
@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Auth", description = "Exchange an API key for a bearer token")
public class AuthResource {

    @Inject
    ApiAuthService apiAuthService;

    @Inject
    AiBridgeConfig config;

    @Context
    HttpHeaders httpHeaders;

    @POST
    @Path("/token")
    @Operation(
            summary = "Exchange an API key for a bearer token",
            description = """
                    The only unauthenticated endpoint. Send `{"api_key": "..."}` and receive an \
                    opaque bearer token to use on every other call.

                    Failed attempts are counted per caller: after 10 failures in 5 minutes the \
                    caller is refused even with the correct key. A locked-out caller and a wrong \
                    key return the same 401.
                    """)
    @APIResponse(responseCode = "200", description = "Token issued")
    @APIResponse(responseCode = "400", description = "api_key missing from the body")
    @APIResponse(responseCode = "401", description = "Invalid API key, or the caller is locked out")
    public Response generateToken(AuthRequest request) {
        if (request == null || request.getApiKey() == null || request.getApiKey().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "api_key is required"))
                    .build();
        }
        String token = apiAuthService.generateToken(request.getApiKey(), callerId());
        if (token == null) {
            // A locked-out caller and a wrong key look identical from outside, so probing cannot
            // tell an attacker whether they have exhausted their budget.
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Invalid API key"))
                    .build();
        }
        return Response.ok(new AuthResponse(token, config.getAuthTokenValidityMinutes())).build();
    }

    /**
     * Identifies the caller for failure counting. Behind a proxy the socket address is the proxy's,
     * so the first hop in {@code X-Forwarded-For} is preferred when present. This is a rate-limit
     * key, not an authentication signal — a spoofed value only lets an attacker choose which
     * bucket they exhaust.
     */
    private String callerId() {
        if (httpHeaders == null) {
            return null;
        }
        String forwarded = httpHeaders.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return sanitise(first);
            }
        }
        String realIp = httpHeaders.getHeaderString("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return sanitise(realIp.trim());
        }
        return "unknown";
    }

    /** Keeps a hostile header out of the cache key namespace. */
    private static String sanitise(String value) {
        String trimmed = value.length() > 64 ? value.substring(0, 64) : value;
        return trimmed.replaceAll("[^A-Za-z0-9._:\\[\\]-]", "_");
    }
}
