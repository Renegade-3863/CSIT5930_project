package hk.ust.csit5930.spider.core;

import java.nio.file.Path;

// record class from Java 14+, immutable data holder for spider configuration
public record SpiderConfig(
    String startUrl,
    int maxPages,
    Path storageDir,
    boolean sameHostOnly,
    int timeoutSeconds,
    String userAgent
) {
}
