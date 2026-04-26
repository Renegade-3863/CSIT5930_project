package hk.ust.csit5930.spider.fetch;

import hk.ust.csit5930.spider.core.SpiderConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PageFetcher {
    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=([\\w\\-]+)", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;
    private final SpiderConfig config;

    public PageFetcher(SpiderConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(config.timeoutSeconds()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_2)
            .build();
    }

    public Optional<Instant> fetchRemoteLastModified(String url) {
        try {
            HttpRequest request = baseRequest(url)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 400) {
                return parseLastModified(response.headers());
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
        } catch (IllegalArgumentException ignored) {
        }
        return Optional.empty();
    }

    public FetchResponse fetch(String url) throws IOException, InterruptedException {
        HttpRequest request = baseRequest(url)
            .GET()
            .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        return buildResponse(url, response);
    }

    /**
     * Asynchronous GET. If {@code ifModifiedSince} is non-null, an {@code If-Modified-Since}
     * header is added so the server can answer with {@code 304 Not Modified} and avoid sending
     * the body. Replaces the previous HEAD-based pre-check (saves one round trip per URL).
     */
    public CompletableFuture<FetchResponse> fetchAsync(String url, Instant ifModifiedSince) {
        HttpRequest.Builder builder = baseRequest(url).GET();
        if (ifModifiedSince != null) {
            builder.header("If-Modified-Since", formatHttpDate(ifModifiedSince));
        }
        HttpRequest request = builder.build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .thenApply(response -> buildResponse(url, response));
    }

    private FetchResponse buildResponse(String url, HttpResponse<byte[]> response) {
        HttpHeaders headers = response.headers();
        int statusCode = response.statusCode();
        if (statusCode == 304) {
            // Not Modified: no body returned, caller should treat as "skip but already fetched".
            return new FetchResponse(
                url,
                response.uri().toString(),
                304,
                headers.firstValue("Content-Type").orElse(""),
                "",
                parseLastModified(headers).orElse(null),
                0L,
                false
            );
        }
        String contentType = headers.firstValue("Content-Type").orElse("text/html");
        boolean isHtml = contentType.toLowerCase(Locale.ROOT).contains("text/html")
            || contentType.toLowerCase(Locale.ROOT).contains("application/xhtml+xml");

        Charset charset = determineCharset(contentType).orElse(StandardCharsets.UTF_8);
        String body;
        try {
            body = new String(response.body(), charset);
        } catch (Exception ex) {
            body = new String(response.body(), StandardCharsets.UTF_8);
        }

        long size = headers.firstValueAsLong("Content-Length").orElse(body.length());
        if (size < 0L) {
            size = body.length();
        }

        return new FetchResponse(
            url,
            response.uri().toString(),
            statusCode,
            contentType,
            body,
            parseLastModified(headers).orElse(null),
            size,
            isHtml
        );
    }

    private String formatHttpDate(Instant instant) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(instant.atZone(ZoneOffset.UTC));
    }

    private HttpRequest.Builder baseRequest(String url) {
        return HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(config.timeoutSeconds()))
            .header("User-Agent", config.userAgent())
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")
            .header("Accept-Encoding", "identity");
    }

    private Optional<Charset> determineCharset(String contentType) {
        Matcher matcher = CHARSET_PATTERN.matcher(contentType);
        if (matcher.find()) {
            try {
                return Optional.of(Charset.forName(matcher.group(1).trim()));
            } catch (Exception ignored) {
            }
        }
        return Optional.empty();
    }

    private Optional<Instant> parseLastModified(HttpHeaders headers) {
        return headers.firstValue("Last-Modified").flatMap(this::parseHttpDate);
    }

    private Optional<Instant> parseHttpDate(String headerValue) {
        try {
            return Optional.of(ZonedDateTime.parse(headerValue, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }
}
