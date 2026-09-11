package com.aibridge.filter;

import com.aibridge.config.AiBridgeConfig;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.regex.Pattern;

/**
 * Serves the Angular entry point for client-side routes so that deep links and page refreshes
 * work.
 *
 * <p>The rule is an exclusion, not an enumeration. An earlier version listed the known SPA paths,
 * which silently broke every time a route was added to {@code app.routes.ts} — a refresh on
 * {@code /admin/llm-configs} fell through to JAX-RS and failed. Anything that is not an API call,
 * not a Quarkus internal, and not a file request is the SPA's to handle.
 */
@ApplicationScoped
public class SpaRoutingFilter {

    /**
     * Matches paths the SPA owns:
     * <ul>
     *   <li>not under an API prefix ({@code /v1}, {@code /api}, {@code /admin/api})</li>
     *   <li>not a Quarkus internal path ({@code /q/...} — health, OpenAPI, Swagger UI)</li>
     *   <li>containing no {@code .}, so requests for real files still reach the static handler</li>
     * </ul>
     */
    static final Pattern SPA_ROUTE =
            Pattern.compile("^/(?!v1(?:/|$)|api(?:/|$)|admin/api(?:/|$)|q(?:/|$))[^.]*$");

    @Inject
    AiBridgeConfig config;

    /** Pretty URL for the static API reference shipped with the UI bundle. */
    static final String API_DOCS_PATH = "/api-docs";

    static final String API_DOCS_FILE = "/assets/api-documentation.html";

    void init(@Observes Router router) {
        // In API-only mode there is no bundle to serve, so rerouting would turn every unknown
        // path into a confusing failure instead of an honest 404.
        if (!config.isUiRequired()) {
            return;
        }
        // Registered before the SPA catch-all: "/api-docs" contains no dot and is not under an
        // API prefix, so the catch-all would otherwise swallow it into the Angular shell.
        router.route(API_DOCS_PATH).handler(ctx -> ctx.reroute(API_DOCS_FILE));
        router.route(API_DOCS_PATH + "/").handler(ctx -> ctx.reroute(API_DOCS_FILE));

        router.getWithRegex(SPA_ROUTE.pattern()).handler(ctx -> ctx.reroute("/index.html"));
    }

    /** Exposed for testing: whether this path should render the SPA shell. */
    static boolean isSpaRoute(String path) {
        return path != null && SPA_ROUTE.matcher(path).matches();
    }
}
