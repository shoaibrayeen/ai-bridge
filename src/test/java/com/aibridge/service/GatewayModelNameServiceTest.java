package com.aibridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibridge.model.LlmConfig;
import com.aibridge.repository.LlmConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GatewayModelNameServiceTest {

    @Mock
    LlmConfigRepository llmConfigRepository;

    @InjectMocks
    GatewayModelNameService service;

    // ---------- slugify ----------

    @ParameterizedTest
    @CsvSource({
        "claude-sonnet-6, claude-sonnet-6",
        "gpt-4o, gpt-4o",
        "GPT-4o, gpt-4o",
        "meta-llama/llama-3-8b-instruct, meta-llama-llama-3-8b-instruct",
        "anthropic.claude-3-5-sonnet-20241022-v2:0, anthropic-claude-3-5-sonnet-20241022-v2-0",
        "'  spaced  model  ', spaced-model",
        "Claude Sonnet 6, claude-sonnet-6",
        "model__with___underscores, model-with-underscores"
    })
    void slugify_normalisesProviderModelNames(String input, String expected) {
        assertEquals(expected, GatewayModelNameService.slugify(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "///", "---", "!!!"})
    void slugify_degeneratesToFallbackSlug(String input) {
        assertEquals("model", GatewayModelNameService.slugify(input));
    }

    @Test
    void slugify_neverLeavesEdgeDashes() {
        String slug = GatewayModelNameService.slugify("--/gpt-4o/--");
        assertFalse(slug.startsWith("-"));
        assertFalse(slug.endsWith("-"));
        assertEquals("gpt-4o", slug);
    }

    // ---------- compose / detect ----------

    @Test
    void compose_buildsPrefixedName() {
        assertEquals("ai-bridge-1-claude-sonnet-6",
                GatewayModelNameService.compose("claude-sonnet-6", 1));
        assertEquals("ai-bridge-12-gpt-4o", GatewayModelNameService.compose("gpt-4o", 12));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ai-bridge-1-gpt-4o", "ai-bridge-999-claude-sonnet-6", "ai-bridge-"})
    void isGatewayModelName_acceptsPrefixedNames(String name) {
        assertTrue(GatewayModelNameService.isGatewayModelName(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"gpt-4o", "claude-sonnet-6", "aibridge-1-gpt-4o", "AI-BRIDGE-1-gpt-4o", ""})
    void isGatewayModelName_rejectsEverythingElse(String name) {
        assertFalse(GatewayModelNameService.isGatewayModelName(name));
    }

    @Test
    void isGatewayModelName_nullIsNotAGatewayName() {
        assertFalse(GatewayModelNameService.isGatewayModelName(null));
    }

    // ---------- assign ----------

    @Test
    void assign_firstConfigOfAModelGetsSequenceOne() {
        when(llmConfigRepository.nextSequenceForSlug("claude-sonnet-6")).thenReturn(1);
        LlmConfig entity = new LlmConfig();

        service.assign(entity, "claude-sonnet-6");

        assertEquals("claude-sonnet-6", entity.getModelSlug());
        assertEquals(1, entity.getModelSequence());
        assertEquals("ai-bridge-1-claude-sonnet-6", entity.getGatewayModelName());
    }

    @Test
    void assign_secondConfigOfSameModelGetsDistinctName() {
        when(llmConfigRepository.nextSequenceForSlug("claude-sonnet-6")).thenReturn(1, 2);

        LlmConfig first = new LlmConfig();
        LlmConfig second = new LlmConfig();
        service.assign(first, "claude-sonnet-6");
        service.assign(second, "claude-sonnet-6");

        assertEquals("ai-bridge-1-claude-sonnet-6", first.getGatewayModelName());
        assertEquals("ai-bridge-2-claude-sonnet-6", second.getGatewayModelName());
        assertNotEquals(first.getGatewayModelName(), second.getGatewayModelName());
    }

    @Test
    void assign_partitionsSequenceBySlugNotRawName() {
        // "GPT-4o" and "gpt-4o" normalise to one slug, so they must share one counter.
        when(llmConfigRepository.nextSequenceForSlug("gpt-4o")).thenReturn(1, 2);

        LlmConfig lower = new LlmConfig();
        LlmConfig upper = new LlmConfig();
        service.assign(lower, "gpt-4o");
        service.assign(upper, "GPT-4o");

        assertEquals("ai-bridge-1-gpt-4o", lower.getGatewayModelName());
        assertEquals("ai-bridge-2-gpt-4o", upper.getGatewayModelName());
    }

    // ---------- reassign ----------

    @Test
    void reassign_doesNothingWhenSlugIsUnchanged() {
        LlmConfig entity = new LlmConfig();
        entity.setModelSlug("gpt-4o");
        entity.setModelSequence(3);
        entity.setGatewayModelName("ai-bridge-3-gpt-4o");

        service.reassignIfModelChanged(entity, "GPT-4o");

        assertEquals("ai-bridge-3-gpt-4o", entity.getGatewayModelName());
        verify(llmConfigRepository, never()).nextSequenceForSlug(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void reassign_allocatesNewNameWhenModelChanges() {
        LlmConfig entity = new LlmConfig();
        entity.setModelSlug("gpt-4o");
        entity.setModelSequence(3);
        entity.setGatewayModelName("ai-bridge-3-gpt-4o");
        when(llmConfigRepository.nextSequenceForSlug("claude-sonnet-6")).thenReturn(1);

        service.reassignIfModelChanged(entity, "claude-sonnet-6");

        assertEquals("ai-bridge-1-claude-sonnet-6", entity.getGatewayModelName());
        assertEquals(1, entity.getModelSequence());
    }

    @Test
    void reassign_assignsWhenNameIsMissingEvenIfSlugMatches() {
        LlmConfig entity = new LlmConfig();
        entity.setModelSlug("gpt-4o");
        when(llmConfigRepository.nextSequenceForSlug("gpt-4o")).thenReturn(7);

        service.reassignIfModelChanged(entity, "gpt-4o");

        assertEquals("ai-bridge-7-gpt-4o", entity.getGatewayModelName());
    }
}
