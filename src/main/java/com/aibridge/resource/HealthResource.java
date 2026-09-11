package com.aibridge.resource;

import com.aibridge.cache.CacheProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
@Path("/admin/api/health")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Admin: Health", description = "Database and cache health")
public class HealthResource {

    @Inject
    EntityManager entityManager;

    @Inject
    CacheProvider cacheProvider;

    @GET
    public Response health() {
        ComponentStatus db = checkDatabase();
        ComponentStatus cache = checkCache();

        boolean up = "UP".equals(db.status()) && "UP".equals(cache.status());
        String overall = up ? "UP" : "DOWN";

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("database", toMap(db));
        components.put("cache", toMap(cache));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", overall);
        body.put("components", components);

        return Response.ok(body).build();
    }

    private static Map<String, Object> toMap(ComponentStatus c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", c.status());
        m.put("latency_ms", c.latencyMs());
        return m;
    }

    private ComponentStatus checkDatabase() {
        long startNs = System.nanoTime();
        try {
            entityManager.createNativeQuery("SELECT 1").getSingleResult();
            long ms = (System.nanoTime() - startNs) / 1_000_000L;
            return new ComponentStatus("UP", ms);
        } catch (Exception e) {
            long ms = (System.nanoTime() - startNs) / 1_000_000L;
            return new ComponentStatus("DOWN", ms);
        }
    }

    private ComponentStatus checkCache() {
        long startNs = System.nanoTime();
        try {
            boolean ok = cacheProvider.ping();
            long ms = (System.nanoTime() - startNs) / 1_000_000L;
            return new ComponentStatus(ok ? "UP" : "DOWN", ms);
        } catch (Exception e) {
            long ms = (System.nanoTime() - startNs) / 1_000_000L;
            return new ComponentStatus("DOWN", ms);
        }
    }

    private record ComponentStatus(String status, long latencyMs) {}
}
