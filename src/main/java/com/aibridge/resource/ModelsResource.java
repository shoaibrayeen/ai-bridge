package com.aibridge.resource;

import com.aibridge.dto.openai.ModelListResponse;
import com.aibridge.dto.openai.ModelObject;
import com.aibridge.model.LlmConfig;
import com.aibridge.repository.LlmConfigRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * OpenAI-compatible model discovery. Lists the gateway-unique model names a tenant may send in the
 * {@code model} field of a chat completion, so an unmodified OpenAI client can enumerate what this
 * gateway offers.
 */
@ApplicationScoped
@Path("/v1/models")
@Produces(MediaType.APPLICATION_JSON)
public class ModelsResource {

    private static final Pattern SAFE_HEADER = Pattern.compile("^[a-zA-Z0-9_-]+$");

    @Inject
    LlmConfigRepository llmConfigRepository;

    @GET
    public Response listModels(@HeaderParam("X-Tenant-ID") String tenantId) {
        if (tenantId != null && !tenantId.isBlank() && !SAFE_HEADER.matcher(tenantId).matches()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid X-Tenant-ID header"))
                    .build();
        }
        String resolvedTenant = (tenantId == null || tenantId.isBlank()) ? null : tenantId;
        List<ModelObject> models = llmConfigRepository.listActiveVisibleTo(resolvedTenant).stream()
                .map(ModelObject::from)
                .toList();
        return Response.ok(new ModelListResponse(models)).build();
    }

    @GET
    @Path("/{model}")
    public Response getModel(
            @HeaderParam("X-Tenant-ID") String tenantId, @PathParam("model") String model) {
        if (tenantId != null && !tenantId.isBlank() && !SAFE_HEADER.matcher(tenantId).matches()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid X-Tenant-ID header"))
                    .build();
        }
        String resolvedTenant = (tenantId == null || tenantId.isBlank()) ? null : tenantId;
        LlmConfig config = llmConfigRepository.findActiveByGatewayModelName(model);
        // A config owned by another tenant is reported the same as one that does not exist.
        if (config == null
                || (config.getTenantId() != null && !config.getTenantId().equals(resolvedTenant))) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "No such model: " + model))
                    .build();
        }
        return Response.ok(ModelObject.from(config)).build();
    }

    /** OpenAI exposes model deletion here; AIBridge manages lifecycle through the admin API. */
    @POST
    @Path("/{model}")
    public Response unsupported(@PathParam("model") String model) {
        return Response.status(Response.Status.METHOD_NOT_ALLOWED)
                .entity(Map.of("error", "Models are managed through /admin/api/llm-configs"))
                .build();
    }
}
