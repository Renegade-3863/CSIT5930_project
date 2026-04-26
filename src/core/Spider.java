package hk.ust.csit5930.spider.core;

import hk.ust.csit5930.spider.fetch.FetchResponse;
import hk.ust.csit5930.spider.fetch.PageFetcher;
import hk.ust.csit5930.spider.html.HtmlDocument;
import hk.ust.csit5930.spider.html.HtmlDocumentParser;
import hk.ust.csit5930.spider.index.PageIndexConsumer;
import hk.ust.csit5930.spider.model.PageInfo;
import hk.ust.csit5930.spider.storage.SpiderRepository;
import hk.ust.csit5930.spider.storage.SpiderState;
import hk.ust.csit5930.spider.util.UrlNormalizer;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class Spider {
    private final SpiderConfig config;
    private final SpiderRepository repository;
    private final PageIndexConsumer indexConsumer;
    private final UrlNormalizer urlNormalizer;
    private final PageFetcher pageFetcher;
    private final HtmlDocumentParser htmlDocumentParser;

    public Spider(SpiderConfig config, SpiderRepository repository, PageIndexConsumer indexConsumer) {
        this.config = config;
        this.repository = repository;
        this.indexConsumer = indexConsumer;
        this.urlNormalizer = new UrlNormalizer();
        this.pageFetcher = new PageFetcher(config);
        this.htmlDocumentParser = new HtmlDocumentParser(urlNormalizer);
    }

    public void crawl() throws Exception {
        SpiderState state = repository.loadOrCreate();
        String normalizedStartUrl = urlNormalizer.normalize(config.startUrl(), null)
            .orElseThrow(() -> new IllegalArgumentException("Invalid start URL: " + config.startUrl()));

        if (!state.hasSeenUrl(normalizedStartUrl)
            && state.getFetchedPageCount() < config.maxPages()) {
            state.enqueueIfUnseen(normalizedStartUrl);
            repository.save(state);
        }

        URI startUri = URI.create(normalizedStartUrl);

        int concurrency = Math.max(1, config.concurrency());
        int saveEvery = Math.max(1, config.saveEveryPages());
        int processedSinceLastSave = 0;

        // Dedicated pool for HTML parsing so it overlaps with network I/O.
        // Sized to concurrency: enough to parse the whole in-flight batch in parallel.
        ExecutorService parserPool = Executors.newFixedThreadPool(concurrency, namedDaemonFactory("spider-parser"));

        try {
            while (state.hasPendingUrls() && state.getFetchedPageCount() < config.maxPages()) {
                int remaining = config.maxPages() - state.getFetchedPageCount();
                int budget = Math.min(concurrency, remaining);

                // Pull a batch from the frontier (single-threaded, no locking needed).
                List<PendingFetch> batch = new ArrayList<>(budget);
                while (batch.size() < budget && state.hasPendingUrls()) {
                    String currentUrl = state.pollFrontier();
                    if (currentUrl == null || !isAllowedUrl(startUri, currentUrl)) {
                        continue;
                    }
                    int pageId = state.getOrCreatePageId(currentUrl);
                    PageInfo pageInfo = state.getPageById(pageId);
                    Instant ifModifiedSince = (pageInfo != null && pageInfo.isFetched())
                        ? pageInfo.getLastModified()
                        : null;
                    batch.add(new PendingFetch(currentUrl, pageId, ifModifiedSince));
                }
                if (batch.isEmpty()) {
                    break;
                }

                // Pipeline: fetchAsync (network) -> parse (parserPool) -> wrap into FetchOutcome.
                // Network and parsing now overlap across the whole batch.
                List<CompletableFuture<FetchOutcome>> futures = new ArrayList<>(batch.size());
                for (PendingFetch p : batch) {
                    CompletableFuture<FetchOutcome> future = pageFetcher.fetchAsync(p.url, p.ifModifiedSince)
                        .thenApplyAsync(response -> parseIfHtml(p, response), parserPool)
                        .exceptionally(error -> new FetchOutcome(p, null, null, error));
                    futures.add(future);
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                // Process all results sequentially in this thread, so SpiderState stays single-writer.
                for (CompletableFuture<FetchOutcome> f : futures) {
                    FetchOutcome outcome = f.join();
                    processOutcome(state, startUri, outcome);
                    processedSinceLastSave++;
                }

                if (processedSinceLastSave >= saveEvery) {
                    repository.save(state);
                    processedSinceLastSave = 0;
                }
            }

            // Final flush so the last partial batch is durable.
            repository.save(state);
            System.out.printf("Crawl finished. Indexed %d pages. State stored in %s%n",
                state.getFetchedPageCount(), config.storageDir());
        } finally {
            parserPool.shutdown();
            if (!parserPool.awaitTermination(5, TimeUnit.SECONDS)) {
                parserPool.shutdownNow();
            }
        }
    }

    /**
     * Runs on the parser pool. Parses HTML eagerly so the main thread only deals with
     * already-parsed HtmlDocument objects (cheap state mutation + disk writes left).
     * HtmlDocumentParser is thread-safe: it allocates a new ParserDelegator + local
     * builders per call, and UrlNormalizer is stateless.
     */
    private FetchOutcome parseIfHtml(PendingFetch p, FetchResponse response) {
        if (response.statusCode() == 304
            || !response.html()
            || response.statusCode() < 200
            || response.statusCode() >= 400) {
            return new FetchOutcome(p, response, null, null);
        }
        try {
            HtmlDocument doc = htmlDocumentParser.parse(p.url, response.body());
            return new FetchOutcome(p, response, doc, null);
        } catch (IOException ex) {
            return new FetchOutcome(p, response, null, ex);
        }
    }

    private void processOutcome(SpiderState state, URI startUri, FetchOutcome outcome) {
        PendingFetch p = outcome.pending();
        PageInfo pageInfo = state.getPageById(p.pageId());
        if (pageInfo == null) {
            return;
        }

        if (outcome.error() != null && outcome.response() == null) {
            // Network-level failure (no response at all).
            Throwable cause = outcome.error();
            if (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            state.setPageFetched(p.pageId(), false);
            pageInfo.setLastError(cause.getClass().getSimpleName() + ": " + cause.getMessage());
            return;
        }

        FetchResponse response = outcome.response();
        pageInfo.setStatusCode(response.statusCode());
        pageInfo.setUrl(p.url());

        // 304 Not Modified: keep existing artifacts, just expand persisted children.
        if (response.statusCode() == 304) {
            pageInfo.setLastError("");
            expandPersistedChildren(state, p.pageId(), startUri);
            return;
        }

        pageInfo.setFetchedAt(Instant.now());
        pageInfo.setLastModified(response.lastModified() != null ? response.lastModified() : pageInfo.getFetchedAt());
        pageInfo.setSize(response.size() > 0L ? response.size() : response.body().length());
        pageInfo.setLastError("");

        if (response.statusCode() < 200 || response.statusCode() >= 400) {
            state.setPageFetched(p.pageId(), false);
            pageInfo.setTitle("");
            pageInfo.setLastError("HTTP status " + response.statusCode());
            return;
        }

        if (!response.html()) {
            state.setPageFetched(p.pageId(), true);
            pageInfo.setTitle(p.url());
            pageInfo.setLastError("Skipped non-HTML content: " + response.contentType());
            return;
        }

        // Parser failed on a content type we expected to be HTML.
        if (outcome.htmlDocument() == null) {
            state.setPageFetched(p.pageId(), false);
            Throwable parseError = outcome.error();
            String msg = parseError == null ? "HTML parse failed" :
                parseError.getClass().getSimpleName() + ": " + parseError.getMessage();
            pageInfo.setLastError(msg);
            return;
        }

        try {
            HtmlDocument htmlDocument = outcome.htmlDocument();
            state.setPageFetched(p.pageId(), true);
            pageInfo.setTitle(htmlDocument.title().isBlank() ? p.url() : htmlDocument.title());
            repository.writeArtifacts(pageInfo, response.body(), htmlDocument);

            for (String childUrl : htmlDocument.links()) {
                if (!isAllowedUrl(startUri, childUrl)) {
                    continue;
                }
                int childId = state.getOrCreatePageId(childUrl);
                state.recordLink(p.pageId(), childId);
                state.enqueueIfUnseen(childUrl);
            }

            indexConsumer.index(pageInfo, htmlDocument);
            System.out.printf("[%d/%d] %s -> pageId=%d%n",
                state.getFetchedPageCount(), config.maxPages(), p.url(), p.pageId());
        } catch (IOException ex) {
            state.setPageFetched(p.pageId(), false);
            pageInfo.setLastError(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private void expandPersistedChildren(SpiderState state, int pageId, URI startUri) {
        for (Integer childId : state.getChildrenOf(pageId)) {
            PageInfo childPage = state.getPageById(childId);
            if (childPage != null && childPage.getUrl() != null && isAllowedUrl(startUri, childPage.getUrl())) {
                state.enqueueIfUnseen(childPage.getUrl());
            }
        }
    }

    private boolean isAllowedUrl(URI startUri, String candidateUrl) {
        if (!config.sameHostOnly()) {
            return true;
        }
        URI candidate = URI.create(candidateUrl);
        String startHost = startUri.getHost();
        String candidateHost = candidate.getHost();
        return startHost != null && startHost.equalsIgnoreCase(candidateHost);
    }

    private static ThreadFactory namedDaemonFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    private record PendingFetch(String url, int pageId, Instant ifModifiedSince) { }

    private record FetchOutcome(PendingFetch pending,
                                FetchResponse response,
                                HtmlDocument htmlDocument,
                                Throwable error) { }
}
