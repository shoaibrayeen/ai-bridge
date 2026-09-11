package com.aibridge.service;

import com.aibridge.model.LlmConfig;
import com.aibridge.repository.LlmConfigRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Assigns each {@link LlmConfig} a service-wide unique model name of the form
 * {@code ai-bridge-<sequence>-<slug>}, for example {@code ai-bridge-2-claude-sonnet-6}.
 *
 * <p>The sequence counts up per slug, which is what allows the same underlying model to be
 * registered more than once — two Claude Sonnet 6 configs with different credentials, endpoints
 * or rate limits get {@code ai-bridge-1-claude-sonnet-6} and {@code ai-bridge-2-claude-sonnet-6}.
 * Clients may send that name in the OpenAI {@code model} field to pin a request to one config.
 */
@ApplicationScoped
public class GatewayModelNameService {

    /** Prefix that marks a model name as belonging to this gateway rather than to a provider. */
    public static final String PREFIX = "ai-bridge-";

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");
    private static final String FALLBACK_SLUG = "model";

    @Inject
    LlmConfigRepository llmConfigRepository;

    /**
     * Normalises a provider model name into a URL- and identifier-safe slug.
     * {@code "meta-llama/Llama-3 8B"} becomes {@code "meta-llama-llama-3-8b"}.
     */
    public static String slugify(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return FALLBACK_SLUG;
        }
        String lowered = modelName.toLowerCase(Locale.ROOT);
        String dashed = NON_ALPHANUMERIC.matcher(lowered).replaceAll("-");
        String trimmed = EDGE_DASHES.matcher(dashed).replaceAll("");
        return trimmed.isEmpty() ? FALLBACK_SLUG : trimmed;
    }

    public static String compose(String slug, int sequence) {
        return PREFIX + sequence + "-" + slug;
    }

    /** True when the supplied model field names a gateway config rather than a provider model. */
    public static boolean isGatewayModelName(String model) {
        return model != null && model.startsWith(PREFIX);
    }

    /**
     * Assigns slug, sequence and gateway model name to {@code entity} for the given model name.
     * Call inside the transaction that persists the entity — sequence allocation is serialised
     * per slug and the unique index on {@code (model_slug, model_sequence)} is the backstop.
     */
    public void assign(LlmConfig entity, String modelName) {
        String slug = slugify(modelName);
        int sequence = llmConfigRepository.nextSequenceForSlug(slug);
        entity.setModelSlug(slug);
        entity.setModelSequence(sequence);
        entity.setGatewayModelName(compose(slug, sequence));
    }

    /**
     * Re-assigns only when the model name normalises to a different slug. Editing an unrelated
     * field must not silently change a name that clients may already be routing on.
     */
    public void reassignIfModelChanged(LlmConfig entity, String newModelName) {
        String slug = slugify(newModelName);
        if (slug.equals(entity.getModelSlug()) && entity.getGatewayModelName() != null) {
            return;
        }
        assign(entity, newModelName);
    }
}
