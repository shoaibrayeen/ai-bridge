package com.aibridge.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
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

        LOG.log(Level.SEVERE, "Unhandled error", exception);
        return json(Response.Status.INTERNAL_SERVER_ERROR, "InternalError",
                "Internal server error", null);
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
