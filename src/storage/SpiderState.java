package hk.ust.csit5930.spider.storage;

import hk.ust.csit5930.spider.model.PageInfo;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SpiderState implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private int nextPageId = 1;
    private final Map<String, Integer> urlToPageId = new LinkedHashMap<>();
    private final Map<Integer, PageInfo> pages = new LinkedHashMap<>();
    private final Map<Integer, LinkedHashSet<Integer>> parentToChildren = new LinkedHashMap<>();
    private final Map<Integer, LinkedHashSet<Integer>> childToParents = new LinkedHashMap<>();
    private final LinkedHashSet<String> seenUrls = new LinkedHashSet<>();
    private final ArrayDeque<String> frontier = new ArrayDeque<>();
    // Cached count of pages with fetched == true. Maintained incrementally via setPageFetched(...).
    // Old serialized states will deserialize this as 0; SpiderRepository.loadOrCreate calls
    // recomputeFetchedCount() to repair the value on load.
    private int fetchedCount = 0;

    public int getOrCreatePageId(String url) {
        Integer existing = urlToPageId.get(url);
        if (existing != null) {
            return existing;
        }
        int pageId = nextPageId++;
        urlToPageId.put(url, pageId);
        pages.put(pageId, new PageInfo(pageId, url));
        return pageId;
    }

    public boolean hasSeenUrl(String url) {
        return seenUrls.contains(url);
    }

    public void enqueueIfUnseen(String url) {
        if (seenUrls.add(url)) {
            frontier.add(url);
        }
    }

    public String pollFrontier() {
        return frontier.poll();
    }

    public boolean hasPendingUrls() {
        return !frontier.isEmpty();
    }

    public Optional<PageInfo> findPageByUrl(String url) {
        Integer pageId = urlToPageId.get(url);
        if (pageId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(pages.get(pageId));
    }

    public PageInfo getPageById(int pageId) {
        return pages.get(pageId);
    }

    public void recordLink(int parentId, int childId) {
        parentToChildren.computeIfAbsent(parentId, ignored -> new LinkedHashSet<>()).add(childId);
        childToParents.computeIfAbsent(childId, ignored -> new LinkedHashSet<>()).add(parentId);
    }

    public Set<Integer> getChildrenOf(int parentId) {
        return Collections.unmodifiableSet(parentToChildren.getOrDefault(parentId, new LinkedHashSet<>()));
    }

    public Set<Integer> getParentsOf(int childId) {
        return Collections.unmodifiableSet(childToParents.getOrDefault(childId, new LinkedHashSet<>()));
    }

    public Map<String, Integer> getUrlToPageId() {
        return Collections.unmodifiableMap(urlToPageId);
    }

    public Collection<PageInfo> getPages() {
        return Collections.unmodifiableCollection(pages.values());
    }

    public Map<Integer, LinkedHashSet<Integer>> getParentToChildren() {
        return Collections.unmodifiableMap(parentToChildren);
    }

    public Map<Integer, LinkedHashSet<Integer>> getChildToParents() {
        return Collections.unmodifiableMap(childToParents);
    }

    public Collection<String> getFrontierSnapshot() {
        return Collections.unmodifiableCollection(frontier);
    }

    public int getFetchedPageCount() {
        return fetchedCount;
    }

    /**
     * Update PageInfo.fetched and keep the cached fetchedCount in sync.
     * Use this instead of calling pageInfo.setFetched directly so the count stays accurate.
     */
    public void setPageFetched(int pageId, boolean fetched) {
        PageInfo page = pages.get(pageId);
        if (page == null) {
            return;
        }
        boolean previous = page.isFetched();
        if (previous == fetched) {
            return;
        }
        page.setFetched(fetched);
        if (fetched) {
            fetchedCount++;
        } else if (fetchedCount > 0) {
            fetchedCount--;
        }
    }

    /**
     * Recompute fetchedCount from scratch. Called after deserialization to repair the cache
     * for state files written before the field existed.
     */
    public void recomputeFetchedCount() {
        int count = 0;
        for (PageInfo page : pages.values()) {
            if (page.isFetched()) {
                count++;
            }
        }
        fetchedCount = count;
    }
}
