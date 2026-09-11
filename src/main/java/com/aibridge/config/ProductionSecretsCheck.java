package com.aibridge.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Refuses to start under the {@code prod} profile when a shipped placeholder secret is still in
 * place.
 *
 * <p>The defaults in {@code application.properties} exist so that a fresh clone runs without
 * ceremony. That convenience becomes a liability the moment the same build reaches production:
 * {@code CHANGE_ME_IN_PRODUCTION} is public knowledge, so anyone can mint an admin token, and the
 * placeholder encryption key makes every stored provider credential recoverable. Failing at
 * startup is far cheaper than discovering either after the fact.
 */
@ApplicationScoped
public class ProductionSecretsCheck {

    /** Values that ship in the repository and must never reach production. */
    static final Set<String> PLACEHOLDERS =
            Set.of("CHANGE_ME_IN_PRODUCTION", "CHANGE_ME_IN_PRODUCTION_32CHARS!");

    @Inject
    AiBridgeConfig config;

    @ConfigProperty(name = "aibridge.security.allow-default-secrets", defaultValue = "false")
    boolean allowDefaultSecrets;

    void onStart(@Observes StartupEvent event) {
        if (!isProductionProfile()) {
            return;
        }
        if (allowDefaultSecrets) {
            return;
        }
        List<String> offenders =
                findPlaceholders(config.getAuthApiKey(), config.getEncryptionKey());
        if (!offenders.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start in the prod profile with placeholder secrets still set: "
                            + String.join(", ", offenders)
                            + ". Set them via environment variables (see .env.example). "
                            + "Set aibridge.security.allow-default-secrets=true only to override "
                            + "this deliberately.");
        }
    }

    /** Names of the properties still holding a shipped placeholder value. */
    static List<String> findPlaceholders(String authApiKey, String encryptionKey) {
        List<String> offenders = new ArrayList<>();
        if (isPlaceholder(authApiKey)) {
            offenders.add("aibridge.auth.api-key");
        }
        if (isPlaceholder(encryptionKey)) {
            offenders.add("aibridge.encryption.key");
        }
        return offenders;
    }

    // Set.of(...).contains(null) throws, and a missing key is a different failure handled
    // elsewhere — it must not turn this guard into a startup NullPointerException.
    private static boolean isPlaceholder(String value) {
        return value != null && PLACEHOLDERS.contains(value);
    }

    private static boolean isProductionProfile() {
        return io.quarkus.runtime.configuration.ConfigUtils.getProfiles().contains("prod");
    }
}
