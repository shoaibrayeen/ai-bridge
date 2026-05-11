package com.aibridge.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibridge.dto.loadtest.LoadTestRequest;
import com.aibridge.dto.loadtest.LoadTestResponse;
import com.aibridge.service.LoadTestService;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoadTestResourceTest {

    @Mock
    LoadTestService loadTestService;

    @InjectMocks
    LoadTestResource loadTestResource;

    @Test
    void startLoadTest_whenSuccessful_returns202WithRunId() {
        LoadTestRequest req = new LoadTestRequest();
        UUID runId = UUID.randomUUID();
        when(loadTestService.startLoadTest(req)).thenReturn(runId);

        Response response = loadTestResource.startLoadTest(req);

        assertEquals(202, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> entity = (Map<String, String>) response.getEntity();
        assertEquals(runId.toString(), entity.get("run_id"));
        verify(loadTestService).startLoadTest(req);
    }

    @Test
    void startLoadTest_whenIllegalArgument_returns400WithMessage() {
        LoadTestRequest req = new LoadTestRequest();
        when(loadTestService.startLoadTest(req))
                .thenThrow(new IllegalArgumentException("parallel too high"));

        Response response = loadTestResource.startLoadTest(req);

        assertEquals(400, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> entity = (Map<String, String>) response.getEntity();
        assertEquals("parallel too high", entity.get("error"));
    }

    @Test
    void startLoadTest_whenIllegalArgumentWithNullMessage_returnsGenericError() {
        LoadTestRequest req = new LoadTestRequest();
        when(loadTestService.startLoadTest(req)).thenThrow(new IllegalArgumentException());

        Response response = loadTestResource.startLoadTest(req);

        assertEquals(400, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> entity = (Map<String, String>) response.getEntity();
        assertEquals("Bad request", entity.get("error"));
    }

    @Test
    void getLoadTest_whenFound_returns200WithBody() {
        UUID runId = UUID.randomUUID();
        LoadTestResponse result = new LoadTestResponse();
        result.setRunId(runId);
        when(loadTestService.getLoadTestResult(runId)).thenReturn(result);

        Response response = loadTestResource.getLoadTest(runId);

        assertEquals(200, response.getStatus());
        assertSame(result, response.getEntity());
    }

    @Test
    void getLoadTest_whenMissing_returns404() {
        UUID runId = UUID.randomUUID();
        when(loadTestService.getLoadTestResult(runId)).thenReturn(null);

        Response response = loadTestResource.getLoadTest(runId);

        assertEquals(404, response.getStatus());
        assertNull(response.getEntity());
    }

    @Test
    void listRecentRuns_delegatesToService() {
        LoadTestResponse a = new LoadTestResponse();
        LoadTestResponse b = new LoadTestResponse();
        when(loadTestService.listRecentRuns()).thenReturn(List.of(a, b));

        List<LoadTestResponse> out = loadTestResource.listRecentRuns();

        assertEquals(2, out.size());
        assertSame(a, out.get(0));
        assertSame(b, out.get(1));
        verify(loadTestService).listRecentRuns();
    }
}
