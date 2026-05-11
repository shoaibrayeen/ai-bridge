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
import jakarta.ws.rs.core.Response;
import java.util.Map;

@ApplicationScoped
@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    ApiAuthService apiAuthService;

    @Inject
    AiBridgeConfig config;

    @POST
    @Path("/token")
    public Response generateToken(AuthRequest request) {
        if (request == null || request.getApiKey() == null || request.getApiKey().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "api_key is required"))
                    .build();
        }
        String token = apiAuthService.generateToken(request.getApiKey());
        if (token == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Invalid API key"))
                    .build();
        }
        return Response.ok(new AuthResponse(token, config.getAuthTokenValidityMinutes())).build();
    }
}
