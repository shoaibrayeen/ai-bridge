package com.aibridge.resource;

import com.aibridge.dto.openai.ChatCompletionChunk;
import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatCompletionResponse;
import com.aibridge.service.ChatCompletionService;
import com.aibridge.service.GatewayModelNameService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.stream.Stream;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.Map;
import java.util.regex.Pattern;

@ApplicationScoped
@Path("/v1/chat/completions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Completions", description = "OpenAI-compatible chat completions")
public class ChatCompletionResource {

    private static final Pattern SAFE_HEADER = Pattern.compile("^[a-zA-Z0-9_-]+$");

    @Inject
    ChatCompletionService chatCompletionService;

    @Inject
    ObjectMapper objectMapper;

    /** Opt-in header for returning the routing trail alongside the completion. */
    public static final String HEADER_INCLUDE_LINEAGE = "X-Include-Lineage";

    @POST
    @Operation(
            summary = "Create a chat completion",
            description = """
                    OpenAI Chat Completions format. Two ways to choose the LLM:

                    1. **Feature routing (default).** Send `X-Feature` (and optionally \
                    `X-Tenant-ID`). The `model` field in the body is ignored — the model comes \
                    from the database. The request walks an ordered failover chain: tenant \
                    primary, tenant fallback, global primary, global fallback.
                    2. **Model pinning.** Put a gateway model name (`ai-bridge-<n>-<model>`, from \
                    `GET /v1/models`) in the `model` field. That selects exactly one config, \
                    `X-Feature` becomes optional, and no failover happens.

                    Set `"stream": true` for a `text/event-stream` of \
                    `chat.completion.chunk` frames terminated by `data: [DONE]`.
                    """)
    @APIResponse(responseCode = "200", description = "Completion, or an SSE stream when stream=true")
    @APIResponse(responseCode = "400", description = "X-Feature missing, or a malformed header")
    @APIResponse(responseCode = "401", description = "Missing or expired bearer token")
    @APIResponse(responseCode = "404", description = "No active config for this tenant and feature")
    @APIResponse(responseCode = "502", description = "Every config in the failover chain was exhausted")
    public Response complete(
            @Parameter(description = "Tenant whose configuration to use. Omit for the global config.", example = "acme")
            @HeaderParam("X-Tenant-ID") String tenantId,
            @Parameter(description = "Feature label that selects the failover chain. Required unless model names a gateway config.", example = "chat")
            @HeaderParam("X-Feature") String feature,
            @Parameter(description = "Set to true to include the routing trail and per-attempt token detail in the response.", example = "true")
            @HeaderParam(HEADER_INCLUDE_LINEAGE) String includeLineage,
            @Valid ChatCompletionRequest request) {
        // A gateway model name in the payload selects a config on its own, so X-Feature is only
        // required when the caller is relying on feature-based routing.
        boolean modelPinned =
                request != null && GatewayModelNameService.isGatewayModelName(request.getModel());
        if (!modelPinned && (feature == null || feature.isBlank())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "X-Feature header is required unless model names an "
                            + GatewayModelNameService.PREFIX + "* config"))
                    .build();
        }
        if (feature != null && !feature.isBlank() && !SAFE_HEADER.matcher(feature).matches()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid X-Feature header"))
                    .build();
        }
        if (tenantId != null && !tenantId.isBlank() && !SAFE_HEADER.matcher(tenantId).matches()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid X-Tenant-ID header"))
                    .build();
        }
        String resolvedTenant = (tenantId == null || tenantId.isBlank()) ? null : tenantId;

        if (request != null && request.isStream()) {
            return streamingResponse(resolvedTenant, feature, request);
        }

        ChatCompletionResponse body = chatCompletionService.complete(resolvedTenant, feature, request);
        // The lineage is always built and logged; it only reaches the wire on request, so the
        // default response stays byte-compatible with what an OpenAI client expects.
        if (!isTruthy(includeLineage)) {
            body.setLineage(null);
        }
        return Response.ok(body).build();
    }

    /**
     * Emits the completion as a text/event-stream in the OpenAI wire format: one {@code data:} line
     * per chunk, terminated by {@code data: [DONE]}.
     *
     * <p>The chunks are written straight to the socket and flushed per frame — buffering them
     * would defeat the point of streaming.
     */
    private Response streamingResponse(
            String tenantId, String feature, ChatCompletionRequest request) {
        ChatCompletionService.StreamingCompletion completion =
                chatCompletionService.completeStream(tenantId, feature, request);

        StreamingOutput output = out -> writeSse(out, completion.chunks());

        return Response.ok(output, MediaType.SERVER_SENT_EVENTS)
                // Proxies that buffer or transform the body would break frame boundaries.
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .build();
    }

    private void writeSse(OutputStream out, Stream<ChatCompletionChunk> chunks) throws IOException {
        try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                Stream<ChatCompletionChunk> source = chunks) {
            Iterator<ChatCompletionChunk> it = source.iterator();
            while (it.hasNext()) {
                writer.write("data: ");
                writer.write(serialise(it.next()));
                writer.write("\n\n");
                writer.flush();
            }
            writer.write("data: " + ChatCompletionChunk.DONE + "\n\n");
            writer.flush();
        }
    }

    private String serialise(ChatCompletionChunk chunk) {
        try {
            return objectMapper.writeValueAsString(chunk);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize completion chunk", e);
        }
    }

    private static boolean isTruthy(String header) {
        return header != null && ("true".equalsIgnoreCase(header.trim()) || "1".equals(header.trim()));
    }
}
