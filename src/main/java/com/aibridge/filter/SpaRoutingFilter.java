package com.aibridge.filter;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class SpaRoutingFilter {

    void init(@Observes Router router) {
        router.getWithRegex("^/(?:playground|admin(?:/(?:health|load-test))?)/?$")
                .handler(ctx -> ctx.reroute("/index.html"));
    }
}
