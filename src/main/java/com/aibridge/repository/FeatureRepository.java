package com.aibridge.repository;

import com.aibridge.model.Feature;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class FeatureRepository implements PanacheRepositoryBase<Feature, UUID> {

    public List<Feature> findByFeature(String feature) {
        return list("feature", feature);
    }

    public long deleteByLlmConfigId(UUID configId) {
        return delete("llmConfig.id = ?1", configId);
    }
}
