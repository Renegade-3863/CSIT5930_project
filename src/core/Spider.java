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
import java.util.Optional;

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

        if (!state.hasSeenUrl(normalizedStartUrl) && !state.hasPendingUrls() && state.getFetchedPageCount() == 0) {
            state.enqueueIfUnseen(normalizedStartUrl);
            repository.save(state);
        } else if (!state.hasSeenUrl(normalizedStartUrl) && state.getFetchedPageCount() < config.maxPages()) {
            state.enqueueIfUnseen(normalizedStartUrl);
            repository.save(state);
        }

        URI startUri = URI.create(normalizedStartUrl);

        while (state.hasPendingUrls() && state.getFetchedPageCount() < config.maxPages()) {
            String currentUrl = state.pollFrontier();
            if (currentUrl == null || !isAllowedUrl(startUri, currentUrl)) {
                repository.save(state);
                continue;
            }

            int pageId = state.getOrCreatePageId(currentUrl);
            PageInfo pageInfo = state.getPageById(pageId);
            CrawlDecision decision = decideFetch(pageInfo, currentUrl);

            if (decision == CrawlDecision.SKIP_UNCHANGED) {
                expandPersistedChildren(state, pageId, startUri);
                repository.save(state);
                continue;
            }

            try {
                FetchResponse response = pageFetcher.fetch(currentUrl);
                pageInfo.setStatusCode(response.statusCode());
                pageInfo.setFetchedAt(Instant.now());
                pageInfo.setLastModified(response.lastModified() != null ? response.lastModified() : pageInfo.getFetchedAt());
                pageInfo.setSize(response.size() > 0L ? response.size() : response.body().length());
                pageInfo.setUrl(currentUrl);
                pageInfo.setLastError("");

                if (response.statusCode() < 200 || response.statusCode() >= 400) {
                    pageInfo.setFetched(false);
                    pageInfo.setTitle("");
                    pageInfo.setLastError("HTTP status " + response.statusCode());
                    repository.save(state);
                    continue;
                }

                if (!response.html()) {
                    pageInfo.setFetched(true);
                    pageInfo.setTitle(currentUrl);
                    pageInfo.setLastError("Skipped non-HTML content: " + response.contentType());
                    repository.save(state);
                    continue;
                }

                HtmlDocument htmlDocument = htmlDocumentParser.parse(currentUrl, response.body());
                pageInfo.setFetched(true);
                pageInfo.setTitle(htmlDocument.title().isBlank() ? currentUrl : htmlDocument.title());
                repository.writeArtifacts(pageInfo, response.body(), htmlDocument);

                for (String childUrl : htmlDocument.links()) {
                    if (!isAllowedUrl(startUri, childUrl)) {
                        continue;
                    }
                    int childId = state.getOrCreatePageId(childUrl);
                    state.recordLink(pageId, childId);
                    state.enqueueIfUnseen(childUrl);
                }

                indexConsumer.index(pageInfo, htmlDocument);
                repository.save(state);
                System.out.printf("[%d/%d] %s -> pageId=%d%n", state.getFetchedPageCount(), config.maxPages(), currentUrl, pageId);
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                pageInfo.setFetched(false);
                pageInfo.setLastError(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                repository.save(state);
            }
        }

        System.out.printf("Crawl finished. Indexed %d pages. State stored in %s%n", state.getFetchedPageCount(), config.storageDir());
    }

    private CrawlDecision decideFetch(PageInfo pageInfo, String url) {
        if (pageInfo == null || !pageInfo.isFetched()) {
            return CrawlDecision.FETCH_NEW;
        }
        Optional<Instant> remoteLastModified = pageFetcher.fetchRemoteLastModified(url);
        if (remoteLastModified.isEmpty()) {
            return CrawlDecision.SKIP_UNCHANGED;
        }
        if (pageInfo.getLastModified() == null) {
            return CrawlDecision.FETCH_UPDATED;
        }
        return remoteLastModified.get().isAfter(pageInfo.getLastModified())
            ? CrawlDecision.FETCH_UPDATED
            : CrawlDecision.SKIP_UNCHANGED;
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
}
