package com.aibridge.filter;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import io.vertx.core.Handler;

@ExtendWith(MockitoExtension.class)
class SpaRoutingFilterTest {

    @Test
    @SuppressWarnings("unchecked")
    void init_registersRouteAndReroutesToIndex() {
        Router router = mock(Router.class);
        Route route = mock(Route.class);
        when(router.getWithRegex(anyString())).thenReturn(route);

        ArgumentCaptor<Handler<RoutingContext>> captor = ArgumentCaptor.forClass(Handler.class);
        when(route.handler(captor.capture())).thenReturn(route);

        SpaRoutingFilter filter = new SpaRoutingFilter();
        filter.init(router);

        verify(router).getWithRegex(eq("^/(?:login|playground|admin(?:/(?:health|load-test))?)/?$"));

        RoutingContext ctx = mock(RoutingContext.class);
        captor.getValue().handle(ctx);
        verify(ctx).reroute("/index.html");
    }
}
