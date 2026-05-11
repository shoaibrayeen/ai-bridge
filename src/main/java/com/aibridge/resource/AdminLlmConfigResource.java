package com.aibridge.resource;

import com.aibridge.dto.admin.LlmConfigRequest;
import com.aibridge.dto.admin.LlmConfigResponse;
import com.aibridge.dto.admin.ValidationResultResponse;
import com.aibridge.model.Feature;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.LlmProvider;
import com.aibridge.repository.FeatureRepository;
import com.aibridge.repository.LlmConfigRepository;
import com.aibridge.repository.LlmProviderRepository;
import com.aibridge.filter.EndpointUrlValidator;
import com.aibridge.service.ConfigResolverService;
import com.aibridge.service.EncryptionService;
import com.aibridge.service.LlmValidationService;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
@Path("/admin/api/llm-configs")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AdminLlmConfigResource {

    @Inject
    LlmConfigRepository llmConfigRepository;

    @Inject
    LlmProviderRepository llmProviderRepository;

    @Inject
    FeatureRepository featureRepository;

    @Inject
    EncryptionService encryptionService;

    @Inject
    ConfigResolverService configResolverService;

    @Inject
    LlmValidationService llmValidationService;

    @Inject
    EndpointUrlValidator endpointUrlValidator;

    @GET
    public List<LlmConfigResponse> listConfigs(
            @QueryParam("tenant_id") String tenantId,
            @QueryParam("feature") String feature,
            @QueryParam("provider_id") UUID providerId) {
        List<LlmConfig> rows = findFiltered(tenantId, feature, providerId);
        return rows.stream().map(LlmConfigResponse::from).toList();
    }

    @GET
    @Path("/{id}")
    public Response getConfig(@PathParam("id") UUID id) {
        return llmConfigRepository
                .findByIdOptional(id)
                .map(LlmConfigResponse::from)
                .map(r -> Response.ok(r).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Transactional
    public Response createConfig(@Valid LlmConfigRequest request) {
        try {
            endpointUrlValidator.validate(request.getEndpointUrl());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build();
        }

        LlmProvider provider =
                llmProviderRepository.findByIdOptional(request.getProviderId()).orElse(null);
        if (provider == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String encryptedCredentials = encryptionService.encrypt(request.getCredentials());
        LlmConfig probe = buildProbeFromRequest(request, provider, encryptedCredentials);
        ValidationResultResponse validation = llmValidationService.validate(probe);
        if (!validation.isValid()) {
            return Response.status(422).entity(validation).build();
        }

        LlmConfig entity = new LlmConfig();
        applyRequestToEntity(entity, request, provider, encryptedCredentials);
        entity.setActive(true);
        attachFeatures(entity, request.getFeatures());

        llmConfigRepository.persist(entity);
        configResolverService.invalidateCacheForConfig(entity);

        return Response.status(Response.Status.CREATED).entity(LlmConfigResponse.from(entity)).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response updateConfig(@PathParam("id") UUID id, @Valid LlmConfigRequest request) {
        try {
            endpointUrlValidator.validate(request.getEndpointUrl());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build();
        }

        LlmConfig entity = llmConfigRepository.findByIdOptional(id).orElse(null);
        if (entity == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        LlmProvider provider =
                llmProviderRepository.findByIdOptional(request.getProviderId()).orElse(null);
        if (provider == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String encryptedCredentials = resolveEncryptedCredentials(entity, request.getCredentials());

        LlmConfig probe = buildProbeFromRequest(request, provider, encryptedCredentials);
        ValidationResultResponse validation = llmValidationService.validate(probe);
        if (!validation.isValid()) {
            return Response.status(422).entity(validation).build();
        }

        configResolverService.invalidateCacheForConfig(entity);

        applyRequestToEntity(entity, request, provider, encryptedCredentials);
        entity.getFeatures().clear();
        attachFeatures(entity, request.getFeatures());

        configResolverService.invalidateCacheForConfig(entity);

        return Response.ok(LlmConfigResponse.from(entity)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteConfig(@PathParam("id") UUID id) {
        LlmConfig entity = llmConfigRepository.findByIdOptional(id).orElse(null);
        if (entity == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        configResolverService.invalidateCacheForConfig(entity);
        entity.setActive(false);
        return Response.noContent().build();
    }

    @POST
    @Path("test")
    public Response testConfig(@Valid LlmConfigRequest request) {
        try {
            endpointUrlValidator.validate(request.getEndpointUrl());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build();
        }

        LlmProvider provider = llmProviderRepository.findByIdOptional(request.getProviderId()).orElse(null);
        if (provider == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String encryptedCredentials = encryptionService.encrypt(request.getCredentials());
        LlmConfig probe = buildProbeFromRequest(request, provider, encryptedCredentials);
        ValidationResultResponse result = llmValidationService.validate(probe);
        return Response.ok(result).build();
    }

    private List<LlmConfig> findFiltered(String tenantId, String feature, UUID providerId) {
        if (tenantId == null && feature == null && providerId == null) {
            return llmConfigRepository.listAll();
        }
        StringBuilder jpql = new StringBuilder("SELECT DISTINCT c FROM LlmConfig c");
        List<Object> params = new ArrayList<>();
        int idx = 1;
        if (feature != null) {
            jpql.append(" JOIN c.features f");
        }
        jpql.append(" WHERE 1=1");
        if (tenantId != null) {
            jpql.append(" AND c.tenantId = ?").append(idx++);
            params.add(tenantId);
        }
        if (feature != null) {
            jpql.append(" AND f.feature = ?").append(idx++);
            params.add(feature);
        }
        if (providerId != null) {
            jpql.append(" AND c.provider.id = ?").append(idx++);
            params.add(providerId);
        }
        return llmConfigRepository.list(jpql.toString(), params.toArray());
    }

    private String resolveEncryptedCredentials(LlmConfig existing, String requestCredentials) {
        String current = existing.getCredentialsEncrypted();
        if (current != null && !current.isBlank()) {
            try {
                String plain = encryptionService.decrypt(current);
                if (plain.equals(requestCredentials)) {
                    return current;
                }
            } catch (Exception ignored) {
                // fall through to re-encrypt
            }
        }
        return encryptionService.encrypt(requestCredentials);
    }

    private static LlmConfig buildProbeFromRequest(
            LlmConfigRequest request, LlmProvider provider, String encryptedCredentials) {
        LlmConfig c = new LlmConfig();
        applyRequestToEntity(c, request, provider, encryptedCredentials);
        return c;
    }

    private static void applyRequestToEntity(
            LlmConfig entity, LlmConfigRequest request, LlmProvider provider, String encryptedCredentials) {
        entity.setTenantId(request.getTenantId());
        entity.setProvider(provider);
        entity.setModelName(request.getModelName());
        entity.setEndpointUrl(request.getEndpointUrl());
        entity.setCredentialsEncrypted(encryptedCredentials);
        entity.setRpsLimit(request.getRpsLimit());
        entity.setRpmLimit(request.getRpmLimit());
        entity.setTpmLimit(request.getTpmLimit());
        entity.setDefaultTemperature(request.getDefaultTemperature());
        entity.setDefaultMaxTokens(request.getDefaultMaxTokens());
        entity.setDefaultTopP(request.getDefaultTopP());
        entity.setDefaultN(request.getDefaultN());
        entity.setDefaultStop(request.getDefaultStop());
        entity.setDefaultPresencePenalty(request.getDefaultPresencePenalty());
        entity.setDefaultFrequencyPenalty(request.getDefaultFrequencyPenalty());
        entity.setQueueTimeoutMs(request.getQueueTimeoutMs() != null ? request.getQueueTimeoutMs() : 5000);
        entity.setFallback(Boolean.TRUE.equals(request.getIsFallback()));
        entity.setPriority(request.getPriority() != null ? request.getPriority() : 0);
        entity.setExtraParams(request.getExtraParams());
    }

    private static void attachFeatures(LlmConfig entity, List<String> featureNames) {
        if (featureNames == null) {
            return;
        }
        for (String name : featureNames) {
            Feature f = new Feature();
            f.setFeature(name);
            f.setLlmConfig(entity);
            entity.getFeatures().add(f);
        }
    }
}
