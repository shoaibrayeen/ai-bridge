package com.aibridge.dto.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CompletionLineageTest {

    @Test
    void add_accumulatesDurationAcrossAttempts() {
        CompletionLineage lineage = new CompletionLineage();
        lineage.add(attempt(1, AttemptOutcome.QUEUE_TIMEOUT, 5000, null));
        lineage.add(attempt(2, AttemptOutcome.SUCCESS, 210, 87));

        assertEquals(5210, lineage.getTotalDurationMs());
        assertEquals(2, lineage.getAttempts().size());
    }

    @Test
    void add_sumsTokensOnlyFromAttemptsThatReportedThem() {
        CompletionLineage lineage = new CompletionLineage();
        lineage.add(attempt(1, AttemptOutcome.RATE_LIMITED, 12, null));
        lineage.add(attempt(2, AttemptOutcome.SUCCESS, 300, 40));
        lineage.add(attempt(3, AttemptOutcome.SUCCESS, 300, 60));

        assertEquals(100, lineage.getBilledTotalTokens());
    }

    @Test
    void billedTokens_staysNullWhenNoAttemptReportedUsage() {
        CompletionLineage lineage = new CompletionLineage();
        lineage.add(attempt(1, AttemptOutcome.QUEUE_TIMEOUT, 5000, null));

        assertNull(lineage.getBilledTotalTokens());
    }

    @Test
    void toLogString_isSingleLineAndOrdered() {
        CompletionLineage lineage = new CompletionLineage();
        lineage.setRequestId("req-7");
        lineage.setTenantId("acme");
        lineage.setFeature("chat");
        lineage.setRouting(CompletionLineage.ROUTING_MODEL);
        lineage.setChainLength(2);
        lineage.add(attempt(1, AttemptOutcome.UNAVAILABLE, 900, null));
        lineage.add(attempt(2, AttemptOutcome.SUCCESS, 210, 87));

        String log = lineage.toLogString();

        assertTrue(log.startsWith("lineage requestId=req-7"), log);
        assertTrue(log.contains("routing=model"), log);
        assertTrue(log.contains("billedTokens=87"), log);
        assertTrue(log.indexOf("UNAVAILABLE") < log.indexOf("SUCCESS"), log);
        assertEquals(1, log.lines().count());
    }

    @Test
    void attemptLogString_includesTokensOnlyWhenKnown() {
        assertEquals(
                "2:ai-bridge-1-gpt-4o/OPENAI=SUCCESS(210ms,87t)",
                attempt(2, AttemptOutcome.SUCCESS, 210, 87).toLogString());
        assertEquals(
                "1:ai-bridge-1-gpt-4o/OPENAI=QUEUE_TIMEOUT(5000ms)",
                attempt(1, AttemptOutcome.QUEUE_TIMEOUT, 5000, null).toLogString());
    }

    @Test
    void serialisesWithSnakeCaseKeysUnderTheExtensionName() throws Exception {
        CompletionLineage lineage = new CompletionLineage();
        lineage.setRequestId("req-1");
        lineage.setRouting(CompletionLineage.ROUTING_FEATURE);
        lineage.add(attempt(1, AttemptOutcome.SUCCESS, 100, 12));

        String json = new ObjectMapper().writeValueAsString(lineage);

        assertTrue(json.contains("\"request_id\":\"req-1\""), json);
        assertTrue(json.contains("\"gateway_model_name\":\"ai-bridge-1-gpt-4o\""), json);
        assertTrue(json.contains("\"total_tokens\":12"), json);
        assertTrue(json.contains("\"billed_total_tokens\":12"), json);
        // Nulls stay off the wire.
        assertTrue(!json.contains("\"detail\""), json);
    }

    private static CallAttempt attempt(
            int sequence, AttemptOutcome outcome, long durationMs, Integer totalTokens) {
        CallAttempt a = new CallAttempt();
        a.setSequence(sequence);
        a.setGatewayModelName("ai-bridge-1-gpt-4o");
        a.setModelName("gpt-4o");
        a.setProvider("OPENAI");
        a.setOutcome(outcome);
        a.setDurationMs(durationMs);
        a.setTotalTokens(totalTokens);
        return a;
    }
}
