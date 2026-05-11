package com.aibridge.dto.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AuthResponseTest {

    @Test
    void defaultConstructor_fieldsAreDefaults() {
        AuthResponse res = new AuthResponse();
        assertNull(res.getToken());
        assertEquals(0, res.getExpiresInMinutes());
    }

    @Test
    void parameterizedConstructor_setsFields() {
        AuthResponse res = new AuthResponse("tok", 60);
        assertEquals("tok", res.getToken());
        assertEquals(60, res.getExpiresInMinutes());
    }

    @Test
    void setters_updateFields() {
        AuthResponse res = new AuthResponse();
        res.setToken("t2");
        res.setExpiresInMinutes(30);
        assertEquals("t2", res.getToken());
        assertEquals(30, res.getExpiresInMinutes());
    }
}
