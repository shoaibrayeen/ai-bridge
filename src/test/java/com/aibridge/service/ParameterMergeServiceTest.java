package com.aibridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aibridge.dto.openai.ChatCompletionRequest;
import com.aibridge.dto.openai.ChatMessage;
import com.aibridge.model.LlmConfig;
import com.aibridge.model.LlmProvider;
import com.aibridge.model.enums.ProviderName;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParameterMergeServiceTest {

    @InjectMocks
    ParameterMergeService parameterMergeService;

    private LlmConfig dbConfig;
    private ChatMessage userMsg;

    @BeforeEach
    void baseConfig() {
        LlmProvider provider = new LlmProvider();
        provider.setName(ProviderName.OPENAI);

        dbConfig = new LlmConfig();
        dbConfig.setProvider(provider);
        dbConfig.setModelName("db-model-alpha");
        dbConfig.setDefaultTemperature(0.2);
        dbConfig.setDefaultMaxTokens(256);
        dbConfig.setDefaultTopP(0.9);
        dbConfig.setDefaultN(1);
        dbConfig.setDefaultStop(List.of("STOP"));
        dbConfig.setDefaultPresencePenalty(0.1);
        dbConfig.setDefaultFrequencyPenalty(0.2);

        userMsg = new ChatMessage();
        userMsg.setRole("user");
        userMsg.setContent("hello");
    }

    @Test
    void whenRequestHasAllParams_requestValuesAreUsedExceptModelFromConfig() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(List.of(userMsg));
        request.setTemperature(0.99);
        request.setMaxTokens(999);
        request.setTopP(0.5);
        request.setN(2);
        request.setStop(List.of("HALT"));
        request.setPresencePenalty(0.3);
        request.setFrequencyPenalty(0.4);

        ChatCompletionRequest merged = parameterMergeService.merge(request, dbConfig);

        assertEquals("db-model-alpha", merged.getModel());
        assertEquals(0.99, merged.getTemperature());
        assertEquals(999, merged.getMaxTokens());
        assertEquals(0.5, merged.getTopP());
        assertEquals(2, merged.getN());
        assertEquals(List.of("HALT"), merged.getStop());
        assertEquals(0.3, merged.getPresencePenalty());
        assertEquals(0.4, merged.getFrequencyPenalty());
    }

    @Test
    void whenRequestHasNoParams_dbDefaultsAreUsed() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(List.of(userMsg));

        ChatCompletionRequest merged = parameterMergeService.merge(request, dbConfig);

        assertEquals("db-model-alpha", merged.getModel());
        assertEquals(0.2, merged.getTemperature());
        assertEquals(256, merged.getMaxTokens());
        assertEquals(0.9, merged.getTopP());
        assertEquals(1, merged.getN());
        assertEquals(List.of("STOP"), merged.getStop());
        assertEquals(0.1, merged.getPresencePenalty());
        assertEquals(0.2, merged.getFrequencyPenalty());
    }

    @Test
    void whenRequestHasSomeParams_mixedRequestAndDbValues() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(List.of(userMsg));
        request.setTemperature(0.7);
        request.setMaxTokens(null);
        request.setTopP(null);
        request.setN(3);

        ChatCompletionRequest merged = parameterMergeService.merge(request, dbConfig);

        assertEquals(0.7, merged.getTemperature());
        assertEquals(256, merged.getMaxTokens());
        assertEquals(0.9, merged.getTopP());
        assertEquals(3, merged.getN());
    }

    @Test
    void modelAlwaysComesFromConfig_evenWhenRequestSpecifiesModel() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(List.of(userMsg));
        request.setModel("client-model-ignored");

        ChatCompletionRequest merged = parameterMergeService.merge(request, dbConfig);

        assertEquals("db-model-alpha", merged.getModel());
    }
}
