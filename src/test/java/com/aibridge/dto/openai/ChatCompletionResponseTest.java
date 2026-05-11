package com.aibridge.dto.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChatCompletionResponseTest {

    @Test
    void gettersAndSetters_roundTrip() {
        ChatCompletionResponse r = new ChatCompletionResponse();
        List<Choice> choices = List.of(new Choice());
        Usage usage = new Usage();

        r.setId("id-1");
        r.setObject("chat.completion");
        r.setCreated(12345L);
        r.setModel("gpt-4");
        r.setChoices(choices);
        r.setUsage(usage);

        assertEquals("id-1", r.getId());
        assertEquals("chat.completion", r.getObject());
        assertEquals(12345L, r.getCreated());
        assertEquals("gpt-4", r.getModel());
        assertSame(choices, r.getChoices());
        assertSame(usage, r.getUsage());
    }

    @Test
    void empty_returnsExpectedShape() {
        String model = "my-model";
        long before = System.currentTimeMillis() / 1000;

        ChatCompletionResponse r = ChatCompletionResponse.empty(model);

        long after = System.currentTimeMillis() / 1000;

        assertEquals("chatcmpl-empty", r.getId());
        assertEquals("chat.completion", r.getObject());
        assertEquals(model, r.getModel());
        assertNotNull(r.getCreated());
        assertTrue(r.getCreated() >= before && r.getCreated() <= after);

        assertNotNull(r.getChoices());
        assertEquals(1, r.getChoices().size());
        Choice c = r.getChoices().get(0);
        assertEquals(0, c.getIndex());
        assertEquals("stop", c.getFinishReason());
        assertNotNull(c.getMessage());
        assertEquals("assistant", c.getMessage().getRole());
        assertEquals("", c.getMessage().getContent());

        assertNotNull(r.getUsage());
        assertEquals(0, r.getUsage().getPromptTokens());
        assertEquals(0, r.getUsage().getCompletionTokens());
        assertEquals(0, r.getUsage().getTotalTokens());
    }
}
