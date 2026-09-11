package com.aibridge.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SpaRoutingFilterTest {

    /**
     * Every path in {@code frontend/src/app/app.routes.ts}. An earlier enumerated regex covered
     * only three of these, so refreshing on the others returned an error page.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "/",
        "/login",
        "/playground",
        "/admin",
        "/admin/",
        "/admin/health",
        "/admin/load-test",
        "/admin/llm-configs",
        "/admin/llm-configs/new",
        "/admin/llm-configs/3f9a2c41-0000-0000-0000-000000000000",
        "/admin/llm-providers",
        "/docs"
    })
    void everyClientRouteRendersTheSpaShell(String path) {
        assertTrue(SpaRoutingFilter.isSpaRoute(path), path + " should render the SPA");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/v1",
        "/v1/models",
        "/v1/chat/completions",
        "/api/auth/token",
        "/admin/api",
        "/admin/api/llm-configs",
        "/admin/api/health"
    })
    void apiPathsAreNeverSwallowed(String path) {
        assertFalse(SpaRoutingFilter.isSpaRoute(path), path + " must reach JAX-RS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/q", "/q/health", "/q/health/ready", "/q/openapi", "/q/swagger-ui"})
    void quarkusInternalPathsAreNeverSwallowed(String path) {
        assertFalse(SpaRoutingFilter.isSpaRoute(path), path + " must reach Quarkus");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/index.html",
        "/favicon.ico",
        "/main-PDRECYTF.js",
        "/styles-XHJLTJX6.css",
        "/assets/logo.png",
        "/api-documentation.html"
    })
    void fileRequestsReachTheStaticHandler(String path) {
        assertFalse(SpaRoutingFilter.isSpaRoute(path), path + " is a file, not a route");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/adminx", "/apifoo", "/v1x", "/qq"})
    void prefixLookalikesAreStillSpaRoutes(String path) {
        // The exclusions are path-segment boundaries, not bare string prefixes.
        assertTrue(SpaRoutingFilter.isSpaRoute(path), path + " is not an API path");
    }
}
