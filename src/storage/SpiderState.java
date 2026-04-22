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
        int count = 0;
        for (PageInfo page : pages.values()) {
            if (page.isFetched()) {
                count++;
            }
        }
        return count;
    }
}
