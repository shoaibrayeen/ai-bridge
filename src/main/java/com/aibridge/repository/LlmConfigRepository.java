package com.aibridge.repository;

import com.aibridge.model.LlmConfig;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class LlmConfigRepository implements PanacheRepositoryBase<LlmConfig, UUID> {

    public List<LlmConfig> findByTenantAndFeature(String tenantId, String feature) {
        return list(
                "SELECT DISTINCT c FROM LlmConfig c JOIN c.features f "
                        + "WHERE (c.tenantId = ?1 OR (?1 IS NULL AND c.tenantId IS NULL)) "
                        + "AND f.feature = ?2 AND c.isActive = true "
                        + "ORDER BY c.isFallback ASC, c.priority ASC",
                tenantId,
                feature);
    }

    public List<LlmConfig> findByTenantAndFeatureOrGlobal(String tenantId, String feature) {
        // No DISTINCT: the join can only produce one row per config because
        // UNIQUE(llm_config_id, feature) allows a single feature row to equal ?2. DISTINCT here
        // is not merely redundant — PostgreSQL rejects SELECT DISTINCT whose ORDER BY uses an
        // expression (the tenant CASE) that is not in the select list, which made this query
        // fail at runtime while every repository-mocking unit test stayed green.
        return list(
                "SELECT c FROM LlmConfig c JOIN c.features f "
                        + "WHERE f.feature = ?2 AND c.isActive = true "
                        + "AND ((?1 IS NOT NULL AND (c.tenantId = ?1 OR c.tenantId IS NULL)) "
                        + "OR (?1 IS NULL AND c.tenantId IS NULL)) "
                        + "ORDER BY CASE WHEN c.tenantId IS NULL THEN 1 ELSE 0 END, "
                        + "c.isFallback ASC, c.priority ASC",
                tenantId,
                feature);
    }

    public List<LlmConfig> listByTenantId(String tenantId) {
        if (tenantId == null) {
            return list("tenantId IS NULL");
        }
        return list("tenantId", tenantId);
    }

    /**
     * Next free sequence number for a model slug. Takes a transaction-scoped advisory lock on the
     * slug first, so two concurrent admin creates cannot read the same maximum; the unique index on
     * {@code (model_slug, model_sequence)} remains the hard guarantee.
     */
    public int nextSequenceForSlug(String slug) {
        getEntityManager()
                .createNativeQuery("SELECT pg_advisory_xact_lock(CAST(?1 AS bigint))")
                .setParameter(1, (long) slug.hashCode())
                .getSingleResult();
        Integer max = getEntityManager()
                .createQuery(
                        "SELECT MAX(c.modelSequence) FROM LlmConfig c WHERE c.modelSlug = :slug",
                        Integer.class)
                .setParameter("slug", slug)
                .getSingleResult();
        return max == null ? 1 : max + 1;
    }

    /** Looks up an active config by its gateway-unique model name. */
    public LlmConfig findActiveByGatewayModelName(String gatewayModelName) {
        return find("gatewayModelName = ?1 AND isActive = true", gatewayModelName).firstResult();
    }

    /**
     * Every active config a tenant may address: its own, plus the global ones. Ordered so the
     * listing is stable across calls.
     */
    public List<LlmConfig> listActiveVisibleTo(String tenantId) {
        if (tenantId == null) {
            return list("isActive = true AND tenantId IS NULL ORDER BY gatewayModelName");
        }
        return list(
                "isActive = true AND (tenantId = ?1 OR tenantId IS NULL) ORDER BY gatewayModelName",
                tenantId);
    }

    public List<LlmConfig> listByFeature(String feature) {
        return list(
                "SELECT DISTINCT c FROM LlmConfig c JOIN c.features f WHERE f.feature = ?1",
                feature);
    }
}
