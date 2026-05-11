package com.aibridge.resource;

import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatCompletionResponse;
import com.aibridge.service.ChatCompletionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.regex.Pattern;

@ApplicationScoped
@Path("/v1/chat/completions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ChatCompletionResource {

    private static final Pattern SAFE_HEADER = Pattern.compile("^[a-zA-Z0-9_-]+$");

    @Inject
    ChatCompletionService chatCompletionService;

    @POST
    public Response complete(
            @HeaderParam("X-Tenant-ID") String tenantId,
            @HeaderParam("X-Feature") String feature,
            @Valid ChatCompletionRequest request) {
        if (feature == null || feature.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "X-Feature header is required"))
                    .build();
        }
        if (!SAFE_HEADER.matcher(feature).matches()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid X-Feature header"))
                    .build();
        }
        if (tenantId != null && !tenantId.isBlank() && !SAFE_HEADER.matcher(tenantId).matches()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid X-Tenant-ID header"))
                    .build();
        }
        String resolvedTenant = (tenantId == null || tenantId.isBlank()) ? null : tenantId;
        ChatCompletionResponse body = chatCompletionService.complete(resolvedTenant, feature, request);
        return Response.ok(body).build();
    }
}
