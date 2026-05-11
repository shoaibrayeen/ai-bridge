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
        return list(
                "SELECT DISTINCT c FROM LlmConfig c JOIN c.features f "
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

    public List<LlmConfig> listByFeature(String feature) {
        return list(
                "SELECT DISTINCT c FROM LlmConfig c JOIN c.features f WHERE f.feature = ?1",
                feature);
    }
}
