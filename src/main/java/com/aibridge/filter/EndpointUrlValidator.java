package com.aibridge.filter;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class EndpointUrlValidator {

    private static final String LOCALHOST = "localhost";

    @ConfigProperty(name = "aibridge.endpoint.allow-http", defaultValue = "false")
    boolean allowHttp;

    @ConfigProperty(name = "aibridge.allowed-provider-hosts")
    Optional<List<String>> allowedProviderHosts;

    /**
     * Validates a provider endpoint URL for scheme, SSRF-safe resolution, and host allowlist.
     *
     * @throws IllegalArgumentException if the URL is invalid or disallowed
     */
    public void validate(String endpointUrl) throws IllegalArgumentException {
        if (endpointUrl == null || endpointUrl.isBlank()) {
            throw new IllegalArgumentException("endpointUrl must not be empty");
        }

        URI uri;
        try {
            uri = URI.create(endpointUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL: not parseable", e);
        }

        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("Invalid URL: scheme and host are required");
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if ("https".equals(scheme)) {
            // ok
        } else if ("http".equals(scheme) && allowHttp) {
            // ok in dev when explicitly allowed
        } else if ("http".equals(scheme)) {
            throw new IllegalArgumentException("HTTP is not allowed for provider endpoints unless aibridge.endpoint.allow-http=true");
        } else {
            throw new IllegalArgumentException("Unsupported URL scheme: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Invalid URL: host is required");
        }

        rejectIfDisallowedHostname(host);

        List<String> allowlist = allowedProviderHosts.orElse(List.of());
        if (allowlist.isEmpty()) {
            throw new IllegalArgumentException("No allowed provider hosts configured (aibridge.allowed-provider-hosts)");
        }
        if (!hostMatchesAllowlist(host, allowlist)) {
            throw new IllegalArgumentException("Host is not in aibridge.allowed-provider-hosts: " + host);
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Host could not be resolved: " + host, e);
        }

        for (InetAddress addr : addresses) {
            if (isPrivateOrNonRoutable(addr)) {
                throw new IllegalArgumentException("Host resolves to a private or non-routable address: " + host);
            }
        }
    }

    private static void rejectIfDisallowedHostname(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (LOCALHOST.equals(h) || "127.0.0.1".equals(h) || "::1".equals(h) || "[::1]".equals(h)) {
            throw new IllegalArgumentException("Localhost and loopback hosts are not allowed: " + host);
        }
        if (h.endsWith(".localhost")) {
            throw new IllegalArgumentException("Localhost and loopback hosts are not allowed: " + host);
        }
    }

    static boolean isPrivateOrNonRoutable(InetAddress addr) {
        if (addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isAnyLocalAddress()) {
            return true;
        }
        byte[] a = addr.getAddress();
        if (a.length == 4) {
            int b0 = a[0] & 0xFF;
            int b1 = a[1] & 0xFF;
            // 10.0.0.0/8
            if (b0 == 10) {
                return true;
            }
            // 172.16.0.0 – 172.31.255.255
            if (b0 == 172 && b1 >= 16 && b1 <= 31) {
                return true;
            }
            // 192.168.0.0/16
            if (b0 == 192 && b1 == 168) {
                return true;
            }
            // 169.254.0.0/16
            if (b0 == 169 && b1 == 254) {
                return true;
            }
            // 127.0.0.0/8
            if (b0 == 127) {
                return true;
            }
        }
        if (a.length == 16) {
            // Unique local IPv6 fc00::/7
            int b0 = a[0] & 0xFF;
            if ((b0 & 0xfe) == 0xfc) {
                return true;
            }
        }
        return false;
    }

    /**
     * Allowlist entries are compared case-insensitively and support two wildcard forms:
     *
     * <ul>
     *   <li>A leading {@code *.} matches the domain itself and any subdomain —
     *       {@code *.cloud.ibm.com} matches both {@code cloud.ibm.com} and
     *       {@code us-south.ml.cloud.ibm.com}.</li>
     *   <li>A {@code *} standing as a whole label matches exactly one label and never crosses a
     *       dot — {@code bedrock-runtime.*.amazonaws.com} matches
     *       {@code bedrock-runtime.us-east-1.amazonaws.com} but not
     *       {@code bedrock-runtime.a.b.amazonaws.com}, and cannot be widened into a lookalike
     *       domain because every other label stays pinned.</li>
     * </ul>
     *
     * <p>A bare {@code *} is deliberately inert: an entry that would allow every host is far more
     * likely to be a mistake than an intent, and silently honouring it defeats the allowlist.
     */
    static boolean hostMatchesAllowlist(String host, List<String> patterns) {
        String h = host.toLowerCase(Locale.ROOT);
        for (String raw : patterns) {
            if (raw == null) {
                continue;
            }
            String p = raw.trim().toLowerCase(Locale.ROOT);
            if (p.isEmpty() || "*".equals(p)) {
                continue;
            }
            if (p.startsWith("*.")) {
                String suffix = p.substring(2);
                if (suffix.isEmpty() || suffix.contains("*")) {
                    continue;
                }
                if (h.equals(suffix) || h.endsWith("." + suffix)) {
                    return true;
                }
            } else if (p.indexOf('*') >= 0) {
                if (labelWildcardMatches(h, p)) {
                    return true;
                }
            } else if (h.equals(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Label-by-label comparison where a {@code *} label matches any single non-empty label.
     * Label counts must be equal, so a wildcard can never absorb extra labels.
     */
    private static boolean labelWildcardMatches(String host, String pattern) {
        String[] hostLabels = host.split("\\.", -1);
        String[] patternLabels = pattern.split("\\.", -1);
        if (hostLabels.length != patternLabels.length) {
            return false;
        }
        for (int i = 0; i < patternLabels.length; i++) {
            String pl = patternLabels[i];
            String hl = hostLabels[i];
            if ("*".equals(pl)) {
                if (hl.isEmpty()) {
                    return false;
                }
            } else if (pl.indexOf('*') >= 0) {
                // Partial-label wildcards (e.g. "api-*.example.com") are not supported; treating
                // them as literals is the safe reading.
                return false;
            } else if (!pl.equals(hl)) {
                return false;
            }
        }
        return true;
    }
}
