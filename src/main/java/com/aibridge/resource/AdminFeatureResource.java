package com.aibridge.resource;

import com.aibridge.model.Feature;
import com.aibridge.repository.FeatureRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@ApplicationScoped
@Path("/admin/api/features")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Admin: Features", description = "Feature labels in use")
public class AdminFeatureResource {

    @Inject
    FeatureRepository featureRepository;

    @GET
    public List<String> listDistinctFeatures() {
        return featureRepository.listAll().stream()
                .map(Feature::getFeature)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}
