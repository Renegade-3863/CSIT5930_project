package hk.ust.csit5930.spider.storage;

import hk.ust.csit5930.spider.html.HtmlDocument;
import hk.ust.csit5930.spider.model.ArtifactPaths;
import hk.ust.csit5930.spider.model.PageInfo;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

public final class SpiderRepository {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);

    private final Path storageDir;
    private final Path stateFile;
    private final Path htmlDir;
    private final Path textDir;
    private final Path exportedDir;
    private final boolean writePageArtifacts;

    public SpiderRepository(Path storageDir) throws IOException {
        this(storageDir, false);
    }

    public SpiderRepository(Path storageDir, boolean writePageArtifacts) throws IOException {
        this.storageDir = storageDir;
        this.stateFile = storageDir.resolve("spider-state.ser");
        this.htmlDir = storageDir.resolve("pages").resolve("html");
        this.textDir = storageDir.resolve("pages").resolve("text");
        this.exportedDir = storageDir.resolve("exports");
        this.writePageArtifacts = writePageArtifacts;
        Files.createDirectories(storageDir);
        Files.createDirectories(exportedDir);
        if (writePageArtifacts) {
            Files.createDirectories(htmlDir);
            Files.createDirectories(textDir);
        }
    }

    public SpiderState loadOrCreate() throws IOException, ClassNotFoundException {
        if (!Files.exists(stateFile)) {
            return new SpiderState();
        }
        try (ObjectInputStream inputStream = new ObjectInputStream(Files.newInputStream(stateFile))) {
            SpiderState state = (SpiderState) inputStream.readObject();
            // Repair the cached fetched count for state files written before the field existed.
            state.recomputeFetchedCount();
            return state;
        }
    }

    public void save(SpiderState state) throws IOException {
        try (ObjectOutputStream outputStream = new ObjectOutputStream(
            Files.newOutputStream(stateFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            outputStream.writeObject(state);
        }
        exportSnapshots(state);
    }

    public ArtifactPaths writeArtifacts(PageInfo pageInfo, String html, HtmlDocument htmlDocument) throws IOException {
        if (!writePageArtifacts) {
            // The indexer reads from indexer_input/page-xxxx-body.txt instead. Skip the duplicate writes.
            pageInfo.setHtmlSnapshotPath("");
            pageInfo.setTextSnapshotPath("");
            return new ArtifactPaths(null, null);
        }
        String filename = String.format("page-%04d", pageInfo.getPageId());
        Path htmlFile = htmlDir.resolve(filename + ".html");
        Path textFile = textDir.resolve(filename + ".txt");
        Files.writeString(htmlFile, html, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(textFile, htmlDocument.bodyText(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        pageInfo.setHtmlSnapshotPath(storageDir.relativize(htmlFile).toString());
        pageInfo.setTextSnapshotPath(storageDir.relativize(textFile).toString());
        return new ArtifactPaths(htmlFile, textFile);
    }

    private void exportSnapshots(SpiderState state) throws IOException {
        writeUrlMapping(state.getUrlToPageId());
        writePages(state.getPages());
        writeLinks(exportedDir.resolve("parent_to_children.tsv"), state.getParentToChildren());
        writeLinks(exportedDir.resolve("child_to_parents.tsv"), state.getChildToParents());
        writeFrontier(state.getFrontierSnapshot());
    }

    private void writeUrlMapping(Map<String, Integer> urlToPageId) throws IOException {
        StringBuilder builder = new StringBuilder("url\tpageId\n");
        for (Map.Entry<String, Integer> entry : urlToPageId.entrySet()) {
            builder.append(sanitize(entry.getKey())).append('\t').append(entry.getValue()).append('\n');
        }
        Files.writeString(exportedDir.resolve("url_to_pageid.tsv"), builder.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void writePages(Collection<PageInfo> pages) throws IOException {
        StringBuilder builder = new StringBuilder("pageId\turl\ttitle\tlastModified\tfetchedAt\tsize\tstatusCode\tfetched\thtmlSnapshot\ttextSnapshot\tlastError\n");
        for (PageInfo page : pages) {
            builder.append(page.getPageId()).append('\t')
                .append(sanitize(page.getUrl())).append('\t')
                .append(sanitize(page.getTitle())).append('\t')
                .append(formatInstant(page.getLastModified())).append('\t')
                .append(formatInstant(page.getFetchedAt())).append('\t')
                .append(page.getSize()).append('\t')
                .append(page.getStatusCode()).append('\t')
                .append(page.isFetched()).append('\t')
                .append(sanitize(page.getHtmlSnapshotPath())).append('\t')
                .append(sanitize(page.getTextSnapshotPath())).append('\t')
                .append(sanitize(page.getLastError())).append('\n');
        }
        Files.writeString(exportedDir.resolve("pages.tsv"), builder.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void writeLinks(Path target, Map<Integer, LinkedHashSet<Integer>> links) throws IOException {
        StringBuilder builder = new StringBuilder("pageId\tlinkedPageIds\n");
        for (Map.Entry<Integer, LinkedHashSet<Integer>> entry : links.entrySet()) {
            builder.append(entry.getKey()).append('\t');
            boolean first = true;
            for (Integer linkedPageId : entry.getValue()) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(linkedPageId);
                first = false;
            }
            builder.append('\n');
        }
        Files.writeString(target, builder.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void writeFrontier(Collection<String> frontier) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (String url : frontier) {
            builder.append(url).append('\n');
        }
        Files.writeString(exportedDir.resolve("frontier.txt"), builder.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "" : TIME_FORMATTER.format(instant);
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }
}
