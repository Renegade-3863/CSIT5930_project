package hk.ust.csit5930.spider.core;

import java.nio.file.Path;

public record SpiderConfig(
    String startUrl,
    int maxPages,
    Path storageDir,
    boolean sameHostOnly,
    int timeoutSeconds,
    String userAgent
) {
}
