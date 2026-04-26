package hk.ust.csit5930.spider.core;

import java.nio.file.Path;

// record class from Java 14+, immutable data holder for spider configuration
public record SpiderConfig(
    String startUrl,
    int maxPages,
    Path storageDir,
    boolean sameHostOnly,
    int timeoutSeconds,
    String userAgent,
    int concurrency,
    int saveEveryPages,
    // When false, the spider does NOT write pages/html/*.html or pages/text/*.txt.
    // The indexer only needs indexer_input/page-xxxx-body.txt, so this saves a lot of disk IO.
    boolean writePageArtifacts
) {
    // Backwards-compatible constructor for older call sites (page artifacts off by default).
    public SpiderConfig(String startUrl, int maxPages, Path storageDir, boolean sameHostOnly,
                        int timeoutSeconds, String userAgent) {
        this(startUrl, maxPages, storageDir, sameHostOnly, timeoutSeconds, userAgent, 8, 25, false);
    }

    public SpiderConfig(String startUrl, int maxPages, Path storageDir, boolean sameHostOnly,
                        int timeoutSeconds, String userAgent, int concurrency, int saveEveryPages) {
        this(startUrl, maxPages, storageDir, sameHostOnly, timeoutSeconds, userAgent,
             concurrency, saveEveryPages, false);
    }
}
