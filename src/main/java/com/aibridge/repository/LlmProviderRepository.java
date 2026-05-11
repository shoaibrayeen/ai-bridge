package com.aibridge.repository;

import com.aibridge.model.LlmProvider;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class LlmProviderRepository implements PanacheRepositoryBase<LlmProvider, UUID> {

    public Optional<LlmProvider> findByName(String name) {
        return find("name", name).firstResultOptional();
    }
}
