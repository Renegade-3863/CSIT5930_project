package hk.ust.csit5930.spider.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

public final class UrlNormalizer {
    public Optional<String> normalize(String rawUrl, URI baseUri) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }

        String trimmed = rawUrl.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("mailto:") || lower.startsWith("tel:")) {
            return Optional.empty();
        }

        try {
            URI resolved = baseUri == null ? new URI(trimmed) : baseUri.resolve(trimmed);
            String scheme = resolved.getScheme();
            if (scheme == null) {
                return Optional.empty();
            }
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                return Optional.empty();
            }

            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            String host = resolved.getHost();
            if (host == null || host.isBlank()) {
                return Optional.empty();
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            int port = resolved.getPort();
            if ((normalizedScheme.equals("http") && port == 80) || (normalizedScheme.equals("https") && port == 443)) {
                port = -1;
            }

            String path = resolved.getPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }
            URI rebuilt = new URI(
                normalizedScheme,
                null,
                normalizedHost,
                port,
                path,
                resolved.getQuery(),
                null
            ).normalize();
            String normalized = rebuilt.toString();
            if (normalized.endsWith("/") && rebuilt.getPath() != null && rebuilt.getPath().length() > 1) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return Optional.of(normalized);
        } catch (URISyntaxException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
