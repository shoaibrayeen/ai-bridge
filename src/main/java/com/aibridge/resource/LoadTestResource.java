package com.aibridge.resource;

import com.aibridge.dto.loadtest.LoadTestRequest;
import com.aibridge.dto.loadtest.LoadTestResponse;
import com.aibridge.service.LoadTestService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
@Path("/admin/api/load-test")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Admin: Load test", description = "Built-in load generator")
public class LoadTestResource {

    @Inject
    LoadTestService loadTestService;

    @POST
    public Response startLoadTest(@Valid LoadTestRequest request) {
        try {
            UUID runId = loadTestService.startLoadTest(request);
            Map<String, String> body = new LinkedHashMap<>();
            body.put("run_id", runId.toString());
            return Response.status(Response.Status.ACCEPTED).entity(body).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage() != null ? e.getMessage() : "Bad request"))
                    .build();
        }
    }

    @GET
    @Path("/{runId}")
    public Response getLoadTest(@PathParam("runId") UUID runId) {
        LoadTestResponse result = loadTestService.getLoadTestResult(runId);
        if (result == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(result).build();
    }

    @GET
    public List<LoadTestResponse> listRecentRuns() {
        return loadTestService.listRecentRuns();
    }
}
