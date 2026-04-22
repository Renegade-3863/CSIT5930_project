package hk.ust.csit5930.spider.fetch;

import java.time.Instant;

public record FetchResponse(
    String requestedUrl,
    String effectiveUrl,
    int statusCode,
    String contentType,
    String body,
    Instant lastModified,
    long size,
    boolean html
) {
}
