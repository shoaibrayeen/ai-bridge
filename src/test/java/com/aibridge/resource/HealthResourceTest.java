package com.aibridge.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.aibridge.cache.CacheProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HealthResourceTest {

    @Mock
    EntityManager entityManager;

    @Mock
    CacheProvider cacheProvider;

    @Mock
    Query nativeQuery;

    @InjectMocks
    HealthResource healthResource;

    @Test
    @SuppressWarnings("unchecked")
    void health_whenDatabaseAndCacheUp_returnsOverallUp() {
        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(1);
        when(cacheProvider.ping()).thenReturn(true);

        jakarta.ws.rs.core.Response res = healthResource.health();
        assertEquals(200, res.getStatus());

        Map<String, Object> body = (Map<String, Object>) res.getEntity();
        assertEquals("UP", body.get("status"));

        Map<String, Object> components = (Map<String, Object>) body.get("components");
        Map<String, Object> db = (Map<String, Object>) components.get("database");
        Map<String, Object> cache = (Map<String, Object>) components.get("cache");
        assertEquals("UP", db.get("status"));
        assertEquals("UP", cache.get("status"));
        assertEquals(Long.class, db.get("latency_ms").getClass());
        assertEquals(Long.class, cache.get("latency_ms").getClass());
    }

    @Test
    @SuppressWarnings("unchecked")
    void health_whenDatabaseDown_returnsOverallDown() {
        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenThrow(new RuntimeException("db unavailable"));
        when(cacheProvider.ping()).thenReturn(true);

        jakarta.ws.rs.core.Response res = healthResource.health();
        Map<String, Object> body = (Map<String, Object>) res.getEntity();
        assertEquals("DOWN", body.get("status"));

        Map<String, Object> components = (Map<String, Object>) body.get("components");
        assertEquals("DOWN", ((Map<String, Object>) components.get("database")).get("status"));
        assertEquals("UP", ((Map<String, Object>) components.get("cache")).get("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void health_whenCacheDown_returnsOverallDown() {
        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(1);
        when(cacheProvider.ping()).thenReturn(false);

        jakarta.ws.rs.core.Response res = healthResource.health();
        Map<String, Object> body = (Map<String, Object>) res.getEntity();
        assertEquals("DOWN", body.get("status"));

        Map<String, Object> components = (Map<String, Object>) body.get("components");
        assertEquals("UP", ((Map<String, Object>) components.get("database")).get("status"));
        assertEquals("DOWN", ((Map<String, Object>) components.get("cache")).get("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void health_whenCachePingThrows_returnsOverallDown() {
        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(1);
        when(cacheProvider.ping()).thenThrow(new RuntimeException("cache down"));

        jakarta.ws.rs.core.Response res = healthResource.health();
        Map<String, Object> body = (Map<String, Object>) res.getEntity();
        assertEquals("DOWN", body.get("status"));
        assertEquals("DOWN", ((Map<String, Object>) ((Map<String, Object>) body.get("components"))
                        .get("cache"))
                .get("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void health_whenBothDown_returnsOverallDown() {
        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenThrow(new RuntimeException("db down"));
        when(cacheProvider.ping()).thenThrow(new RuntimeException("cache down"));

        jakarta.ws.rs.core.Response res = healthResource.health();
        Map<String, Object> body = (Map<String, Object>) res.getEntity();
        assertEquals("DOWN", body.get("status"));

        Map<String, Object> components = (Map<String, Object>) body.get("components");
        assertEquals("DOWN", ((Map<String, Object>) components.get("database")).get("status"));
        assertEquals("DOWN", ((Map<String, Object>) components.get("cache")).get("status"));
    }
}
