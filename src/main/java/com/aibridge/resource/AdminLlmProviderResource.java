package com.aibridge.resource;

import com.aibridge.dto.admin.LlmProviderRequest;
import com.aibridge.dto.admin.LlmProviderResponse;
import com.aibridge.model.LlmProvider;
import com.aibridge.model.enums.AuthType;
import com.aibridge.model.enums.ProviderName;
import com.aibridge.repository.LlmProviderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Path("/admin/api/llm-providers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AdminLlmProviderResource {

    @Inject
    LlmProviderRepository llmProviderRepository;

    @GET
    public List<LlmProviderResponse> listProviders() {
        return llmProviderRepository.listAll().stream().map(LlmProviderResponse::from).toList();
    }

    @GET
    @Path("/{id}")
    public Response getProvider(@PathParam("id") UUID id) {
        return llmProviderRepository
                .findByIdOptional(id)
                .map(LlmProviderResponse::from)
                .map(r -> Response.ok(r).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Transactional
    public Response createProvider(@Valid LlmProviderRequest request) {
        LlmProvider entity = new LlmProvider();
        mapRequestToEntity(request, entity);
        llmProviderRepository.persist(entity);
        return Response.status(Response.Status.CREATED).entity(LlmProviderResponse.from(entity)).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response updateProvider(@PathParam("id") UUID id, @Valid LlmProviderRequest request) {
        LlmProvider entity = llmProviderRepository.findByIdOptional(id).orElse(null);
        if (entity == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        mapRequestToEntity(request, entity);
        return Response.ok(LlmProviderResponse.from(entity)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteProvider(@PathParam("id") UUID id) {
        if (llmProviderRepository.findByIdOptional(id).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        llmProviderRepository.deleteById(id);
        return Response.noContent().build();
    }

    private static void mapRequestToEntity(LlmProviderRequest request, LlmProvider entity) {
        entity.setName(ProviderName.valueOf(request.getName().trim().toUpperCase()));
        entity.setAuthType(AuthType.valueOf(request.getAuthType().trim().toUpperCase()));
        entity.setAuthEndpoint(request.getAuthEndpoint());
    }
}
