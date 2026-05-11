package com.aibridge.dto.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AuthRequestTest {

    @Test
    void defaultConstructor_fieldsAreNull() {
        AuthRequest req = new AuthRequest();
        assertNull(req.getApiKey());
    }

    @Test
    void parameterizedConstructor_setsField() {
        AuthRequest req = new AuthRequest("my-key");
        assertEquals("my-key", req.getApiKey());
    }

    @Test
    void setApiKey_updatesField() {
        AuthRequest req = new AuthRequest();
        req.setApiKey("updated");
        assertEquals("updated", req.getApiKey());
    }
}
