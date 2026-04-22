package hk.ust.csit5930.spider.index;

import hk.ust.csit5930.spider.html.HtmlDocument;
import hk.ust.csit5930.spider.model.PageInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class FilePageIndexConsumer implements PageIndexConsumer {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);

    private final Path inputDir;
    private final Path manifestPath;

    public FilePageIndexConsumer(Path inputDir) throws IOException {
        this.inputDir = inputDir;
        this.manifestPath = inputDir.resolve("manifest.tsv");
        Files.createDirectories(inputDir);
        if (!Files.exists(manifestPath)) {
            Files.writeString(
                manifestPath,
                "pageId\turl\ttitle\tlastModified\tsize\tbodyFile\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
        }
    }

    @Override
    public synchronized void index(PageInfo pageInfo, HtmlDocument htmlDocument) throws IOException {
        String filename = String.format("page-%04d-body.txt", pageInfo.getPageId());
        Path bodyFile = inputDir.resolve(filename);
        Files.writeString(bodyFile, htmlDocument.bodyText(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        String lastModified = pageInfo.getLastModified() == null ? "" : TIME_FORMATTER.format(pageInfo.getLastModified());
        String line = pageInfo.getPageId() + "\t"
            + sanitize(pageInfo.getUrl()) + "\t"
            + sanitize(pageInfo.getTitle()) + "\t"
            + lastModified + "\t"
            + pageInfo.getSize() + "\t"
            + bodyFile.getFileName() + System.lineSeparator();
        Files.writeString(manifestPath, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }
}
