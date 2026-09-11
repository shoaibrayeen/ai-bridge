package com.aibridge.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProductionSecretsCheckTest {

    @Test
    void bothPlaceholdersAreReported() {
        List<String> offenders = ProductionSecretsCheck.findPlaceholders(
                "CHANGE_ME_IN_PRODUCTION", "CHANGE_ME_IN_PRODUCTION_32CHARS!");

        assertEquals(List.of("aibridge.auth.api-key", "aibridge.encryption.key"), offenders);
    }

    @Test
    void onlyTheOffendingPropertyIsNamed() {
        assertEquals(
                List.of("aibridge.auth.api-key"),
                ProductionSecretsCheck.findPlaceholders(
                        "CHANGE_ME_IN_PRODUCTION", "a-real-32-character-secret-key!!"));
        assertEquals(
                List.of("aibridge.encryption.key"),
                ProductionSecretsCheck.findPlaceholders(
                        "a-real-api-key", "CHANGE_ME_IN_PRODUCTION_32CHARS!"));
    }

    @Test
    void realSecretsPass() {
        assertTrue(ProductionSecretsCheck.findPlaceholders(
                        "a-real-api-key", "a-real-32-character-secret-key!!")
                .isEmpty());
    }

    @Test
    void nullsAreNotTreatedAsPlaceholders() {
        // A missing key fails elsewhere (EncryptionConfig enforces the 32-char length); this check
        // is only about the values that ship in the repository.
        assertTrue(ProductionSecretsCheck.findPlaceholders(null, null).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CHANGE_ME_IN_PRODUCTION", "CHANGE_ME_IN_PRODUCTION_32CHARS!"})
    void everyShippedPlaceholderIsCovered(String placeholder) {
        // Guards against a new default being added to application.properties without being listed.
        assertTrue(ProductionSecretsCheck.PLACEHOLDERS.contains(placeholder));
    }

    @Test
    void placeholderSetMatchesTheShippedDefaults() throws Exception {
        String props = new String(
                ProductionSecretsCheckTest.class
                        .getClassLoader()
                        .getResourceAsStream("application.properties")
                        .readAllBytes());

        for (String line : props.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith("aibridge.auth.api-key=")
                    || trimmed.startsWith("aibridge.encryption.key=")) {
                String value = trimmed.substring(trimmed.indexOf('=') + 1);
                assertTrue(
                        ProductionSecretsCheck.PLACEHOLDERS.contains(value),
                        "application.properties ships '" + value
                                + "' but ProductionSecretsCheck does not know it, so prod would "
                                + "start with a public secret: " + trimmed);
            }
        }
    }
}
