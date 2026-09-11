package com.aibridge.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class.getName());

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof ConfigNotFoundException e) {
            return json(Response.Status.NOT_FOUND, "ConfigNotFound", safeMessage(e), null);
        }
        if (exception instanceof ProviderUnavailableException e) {
            return json(Response.Status.BAD_GATEWAY, "ProviderUnavailable", safeMessage(e), null);
        }
        if (exception instanceof LlmValidationException e) {
            return json(422, "LlmValidation", safeMessage(e), null);
        }
        if (exception instanceof EncryptionException e) {
            LOG.log(Level.SEVERE, "Encryption error", e);
            return json(Response.Status.INTERNAL_SERVER_ERROR, "Encryption", "Encryption error", null);
        }
        if (exception instanceof ConstraintViolationException e) {
            List<Map<String, String>> violations = new ArrayList<>();
            for (ConstraintViolation<?> v : e.getConstraintViolations()) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("path", v.getPropertyPath() != null ? v.getPropertyPath().toString() : "");
                row.put("message", v.getMessage());
                violations.add(row);
            }
            String summary = e.getConstraintViolations().stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining("; "));
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("violations", violations);
            return json(Response.Status.BAD_REQUEST, "ConstraintViolation",
                    summary.isEmpty() ? "Validation failed" : summary, details);
        }

        if (exception instanceof WebApplicationException e) {
            // JAX-RS already decided the status — a 404 for an unknown path, a 405 for a wrong
            // method. Collapsing those into 500 both lies to the caller and buries real failures
            // under SEVERE noise from every bot that probes an unknown URL.
            int status = e.getResponse() == null
                    ? Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()
                    : e.getResponse().getStatus();
            if (status >= 500) {
                LOG.log(Level.SEVERE, "Server error", e);
            } else if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "Client error " + status, e);
            }
            return json(status, typeFor(status), clientSafeMessage(e, status), null);
        }

        LOG.log(Level.SEVERE, "Unhandled error", exception);
        return json(Response.Status.INTERNAL_SERVER_ERROR, "InternalError",
                "Internal server error", null);
    }

    private static String typeFor(int status) {
        return switch (status) {
            case 400 -> "BadRequest";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "NotFound";
            case 405 -> "MethodNotAllowed";
            case 406 -> "NotAcceptable";
            case 415 -> "UnsupportedMediaType";
            default -> status >= 500 ? "ServerError" : "ClientError";
        };
    }

    /** A 5xx message can carry internals, so only client errors echo their own text. */
    private static String clientSafeMessage(WebApplicationException e, int status) {
        if (status >= 500) {
            return "Internal server error";
        }
        return safeMessage(e);
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        return m != null && !m.isBlank() ? m : t.getClass().getSimpleName();
    }

    private static Response json(Response.Status status, String type, String message,
            Map<String, Object> extraErrorFields) {
        return json(status.getStatusCode(), type, message, extraErrorFields);
    }

    private static Response json(int statusCode, String type, String message,
            Map<String, Object> extraErrorFields) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("type", type);
        err.put("message", message);
        err.put("status", statusCode);
        if (extraErrorFields != null && !extraErrorFields.isEmpty()) {
            err.putAll(extraErrorFields);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", err);
        return Response.status(statusCode)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}
