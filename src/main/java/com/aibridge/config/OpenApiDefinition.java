package com.aibridge.config;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Document-level OpenAPI metadata. Everything under {@code /v1} and {@code /admin/api} needs the
 * bearer token issued by {@code POST /api/auth/token}, so the requirement is declared once here
 * rather than repeated on every operation.
 */
@OpenAPIDefinition(
        info = @Info(
                title = "AIBridge Gateway API",
                version = "1.0.0",
                description = """
                        An OpenAI-compatible gateway in front of five LLM providers.

                        **Routing.** A request is routed by the `X-Tenant-ID` and `X-Feature` \
                        headers to an ordered failover chain. Alternatively, send a gateway model \
                        name (`ai-bridge-<n>-<model>`) in the `model` field to pin the request to \
                        one specific config — then `X-Feature` is optional and no failover happens.

                        **Auth.** Exchange your API key for a bearer token at `POST /api/auth/token`, \
                        then send `Authorization: Bearer <token>` on everything else. Provider \
                        credentials never leave the gateway.
                        """),
        tags = {
                @Tag(name = "Auth", description = "Exchange an API key for a bearer token"),
                @Tag(name = "Completions", description = "OpenAI-compatible chat completions"),
                @Tag(name = "Models", description = "Discover the gateway model names you can call"),
                @Tag(name = "Admin: Configs", description = "LLM configurations — validated against the provider before they are saved"),
                @Tag(name = "Admin: Providers", description = "Provider registry and auth types"),
                @Tag(name = "Admin: Features", description = "Feature labels in use"),
                @Tag(name = "Admin: Load test", description = "Built-in load generator"),
                @Tag(name = "Admin: Health", description = "Database and cache health")
        })
@SecurityScheme(
        securitySchemeName = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        description = "Token from POST /api/auth/token")
@SecurityRequirement(name = "bearerAuth")
public class OpenApiDefinition extends Application {
}
