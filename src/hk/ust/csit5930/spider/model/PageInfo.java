package hk.ust.csit5930.spider.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

public final class PageInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int pageId;
    private String url;
    private String title;
    private Instant lastModified;
    private Instant fetchedAt;
    private long size;
    private int statusCode;
    private boolean fetched;
    private String htmlSnapshotPath;
    private String textSnapshotPath;
    private String lastError;

    public PageInfo(int pageId, String url) {
        this.pageId = pageId;
        this.url = url;
        this.title = "";
        this.size = 0L;
        this.statusCode = 0;
        this.fetched = false;
        this.lastError = "";
    }

    public int getPageId() {
        return pageId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public boolean isFetched() {
        return fetched;
    }

    public void setFetched(boolean fetched) {
        this.fetched = fetched;
    }

    public String getHtmlSnapshotPath() {
        return htmlSnapshotPath;
    }

    public void setHtmlSnapshotPath(String htmlSnapshotPath) {
        this.htmlSnapshotPath = htmlSnapshotPath;
    }

    public String getTextSnapshotPath() {
        return textSnapshotPath;
    }

    public void setTextSnapshotPath(String textSnapshotPath) {
        this.textSnapshotPath = textSnapshotPath;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError == null ? "" : lastError;
    }
}
