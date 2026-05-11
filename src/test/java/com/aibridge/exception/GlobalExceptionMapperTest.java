package com.aibridge.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionMapperTest {

    @Mock
    @SuppressWarnings("rawtypes")
    ConstraintViolation violation;

    @Mock
    Path propertyPath;

    @InjectMocks
    GlobalExceptionMapper mapper;

    @SuppressWarnings("unchecked")
    private Map<String, Object> errorMap(Response response) {
        assertNotNull(response.getEntity());
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertInstanceOf(Map.class, body.get("error"));
        return (Map<String, Object>) body.get("error");
    }

    @Test
    void configNotFound_returns404() {
        Response r = mapper.toResponse(new ConfigNotFoundException("t1", "chat"));
        assertEquals(404, r.getStatus());
        Map<String, Object> err = errorMap(r);
        assertEquals("ConfigNotFound", err.get("type"));
        assertEquals(404, err.get("status"));
        assertNotNull(err.get("message"));
    }

    @Test
    void configNotFound_withCause_returns404() {
        Response r = mapper.toResponse(new ConfigNotFoundException("t1", "chat", new RuntimeException("root")));
        assertEquals(404, r.getStatus());
        Map<String, Object> err = errorMap(r);
        assertEquals("ConfigNotFound", err.get("type"));
    }

    @Test
    void providerUnavailable_returns502() {
        Response r = mapper.toResponse(new ProviderUnavailableException("upstream down"));
        assertEquals(502, r.getStatus());
        Map<String, Object> err = errorMap(r);
        assertEquals("ProviderUnavailable", err.get("type"));
        assertEquals("upstream down", err.get("message"));
        assertEquals(502, err.get("status"));
    }

    @Test
    void providerUnavailable_blankMessage_usesClassName() {
        Response r = mapper.toResponse(new ProviderUnavailableException("  "));
        Map<String, Object> err = errorMap(r);
        assertEquals("ProviderUnavailable", err.get("type"));
        assertEquals("ProviderUnavailableException", err.get("message"));
    }

    @Test
    void providerUnavailable_nullMessage_usesClassName() {
        Response r = mapper.toResponse(new ProviderUnavailableException(null));
        Map<String, Object> err = errorMap(r);
        assertEquals("ProviderUnavailableException", err.get("message"));
    }

    @Test
    void llmValidation_returns422() {
        Response r = mapper.toResponse(new LlmValidationException("bad output"));
        assertEquals(422, r.getStatus());
        Map<String, Object> err = errorMap(r);
        assertEquals("LlmValidation", err.get("type"));
        assertEquals("bad output", err.get("message"));
        assertEquals(422, err.get("status"));
    }

    @Test
    void llmValidation_blankMessage_usesClassName() {
        Response r = mapper.toResponse(new LlmValidationException(" \t "));
        Map<String, Object> err = errorMap(r);
        assertEquals("LlmValidationException", err.get("message"));
    }

    @Test
    void encryption_withMessage_returns500SanitizedMessage() {
        Response r = mapper.toResponse(new EncryptionException("secret detail", new IllegalStateException("x")));
        assertEquals(500, r.getStatus());
        Map<String, Object> err = errorMap(r);
        assertEquals("Encryption", err.get("type"));
        assertEquals("Encryption error", err.get("message"));
        assertEquals(500, err.get("status"));
    }

    @Test
    void encryption_causeOnly_returns500SanitizedMessage() {
        Response r = mapper.toResponse(new EncryptionException(new RuntimeException("hidden")));
        assertEquals(500, r.getStatus());
        Map<String, Object> err = errorMap(r);
        assertEquals("Encryption", err.get("type"));
        assertEquals("Encryption error", err.get("message"));
    }

    @Test
    void constraintViolation_returns400WithViolations() {
        when(violation.getPropertyPath()).thenReturn(propertyPath);
        when(propertyPath.toString()).thenReturn("body.model");
        when(violation.getMessage()).thenReturn("must not be blank");

        Set<ConstraintViolation<?>> violations = new LinkedHashSet<>();
        violations.add(violation);
        ConstraintViolationException ex = new ConstraintViolationException("failed", violations);

        Response r = mapper.toResponse(ex);
        assertEquals(400, r.getStatus());
        Map<String, Object> err = errorMap(r);
        assertEquals("ConstraintViolation", err.get("type"));
        assertEquals("must not be blank", err.get("message"));
        assertEquals(400, err.get("status"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> list = (List<Map<String, String>>) err.get("violations");
        assertEquals(1, list.size());
        assertEquals("body.model", list.get(0).get("path"));
        assertEquals("must not be blank", list.get(0).get("message"));
    }

    @Test
    void constraintViolation_nullPropertyPath_usesEmptyPath() {
        when(violation.getPropertyPath()).thenReturn(null);
        when(violation.getMessage()).thenReturn("invalid");

        Set<ConstraintViolation<?>> violations = Set.of(violation);
        Response r = mapper.toResponse(new ConstraintViolationException("x", violations));
        Map<String, Object> err = errorMap(r);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> list = (List<Map<String, String>>) err.get("violations");
        assertEquals("", list.get(0).get("path"));
    }

    @Test
    void constraintViolation_emptyViolations_usesDefaultSummary() {
        Set<ConstraintViolation<?>> empty = Set.of();
        Response r = mapper.toResponse(new ConstraintViolationException("x", empty));
        Map<String, Object> err = errorMap(r);
        assertEquals("Validation failed", err.get("message"));
    }

    @Test
    void queueTimeout_returnsInternalError() {
        Response r = mapper.toResponse(new QueueTimeoutException("cfg-1"));
        assertEquals(500, r.getStatus());
        Map<String, Object> err = errorMap(r);
        assertEquals("InternalError", err.get("type"));
        assertEquals("Internal server error", err.get("message"));
        assertEquals(500, err.get("status"));
    }

    @Test
    void genericThrowable_returnsInternalError() {
        Response r = mapper.toResponse(new RuntimeException("boom"));
        assertEquals(500, r.getStatus());
        Map<String, Object> err = errorMap(r);
        assertEquals("InternalError", err.get("type"));
        assertEquals("Internal server error", err.get("message"));
    }

    @Test
    void jsonHelper_nonNullEmptyExtraFields_omitsMergingExtras() throws Exception {
        Method m =
                GlobalExceptionMapper.class.getDeclaredMethod(
                        "json", Response.Status.class, String.class, String.class, Map.class);
        m.setAccessible(true);
        Map<String, Object> emptyExtras = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Response r =
                (Response)
                        m.invoke(
                                null,
                                Response.Status.BAD_REQUEST,
                                "TestType",
                                "hello",
                                emptyExtras);

        assertEquals(400, r.getStatus());
        Map<String, Object> err = errorMap(r);
        assertEquals(3, err.size());
        assertEquals("TestType", err.get("type"));
        assertEquals("hello", err.get("message"));
        assertEquals(400, err.get("status"));
    }
}
