# Copilot instructions for CSIT5930 spider

## Big picture (read this first)
- This project is a **standalone Java crawler** (no Maven/Gradle) with one CLI entrypoint: `src/SpiderApplication.java`.
- Core flow is in `src/core/Spider.java`: load state -> BFS crawl frontier -> fetch/parse -> persist artifacts/state -> export TSV snapshots -> append indexer handoff files.
- State is intentionally persistent across runs via Java serialization (`data/spider/spider-state.ser`) handled by `src/storage/SpiderRepository.java` + `src/storage/SpiderState.java`.
- The crawler is designed for **incremental reruns**: for previously fetched pages it issues `HEAD` and only re-fetches when `Last-Modified` is newer (`PageFetcher.fetchRemoteLastModified`).
- Page identity is by normalized URL (`src/util/UrlNormalizer.java`), then mapped to stable `pageId` in `SpiderState`.

## Component boundaries
- `core/`: crawl orchestration and decisions (`CrawlDecision`, `SpiderConfig`, `Spider`).
- `fetch/`: HTTP logic only (`java.net.http.HttpClient`), charset detection, content-type handling.
- `html/`: HTML parsing + link extraction (`ParserDelegator`), returns `HtmlDocument`.
- `storage/`: serialized state + exported snapshots (`exports/*.tsv`, `frontier.txt`, page artifacts).
- `index/`: indexer handoff contract (`PageIndexConsumer`) and file implementation (`indexer_input/manifest.tsv` + `page-xxxx-body.txt`).

## Developer workflows
- Build: `powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1`
- Run: `powershell -ExecutionPolicy Bypass -File .\scripts\run-spider.ps1 -StartUrl "<url>" -MaxPages 300 -StorageDir "data\spider" -SameHostOnly true`
- Direct run (after build): `java -cp out hk.ust.csit5930.spider.SpiderApplication <startUrl> <maxPages> [storageDir] [sameHostOnly]`
- Fresh crawl behavior depends on storage dir; use a new `data/spider-*` dir or delete old state to avoid resume behavior.

## Project-specific patterns to preserve
- Keep URL normalization strict: drop fragments, enforce http/https, lowercase scheme/host, remove default ports, ignore `javascript:`, `mailto:`, `tel:` links.
- Keep BFS + dedupe semantics: only `enqueueIfUnseen` should control frontier insertion and `seenUrls` tracking.
- Preserve `pageId` stability and link graph updates (`recordLink(parentId, childId)`) whenever adding child-link behavior.
- `SpiderRepository.save()` intentionally triggers full snapshot exports every save; do not bypass this in normal crawl flow.
- `FilePageIndexConsumer.index()` is append-based manifest output; preserve header + tab-separated schema.
- Non-HTML responses are recorded as fetched metadata but skipped for parsing/index body extraction (see `Spider.crawl()`).

## When changing code
- Prefer changes inside the existing package structure (`core`, `fetch`, `html`, `storage`, `index`, `util`) instead of adding cross-cutting utilities.
- If adding fields to persisted models (`SpiderState`, `PageInfo`), consider serialization compatibility and exported TSV columns.
- Keep outputs UTF-8 and sanitized for TSV safety (`\t`, `\r`, `\n` replaced) as implemented in repository/index writer.
- Validate with a short crawl and inspect generated files under `data/spider/exports` and `data/spider/indexer_input`.
