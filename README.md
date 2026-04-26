# CSIT5930 Spider Module

This repository contains a Java implementation of the `spider` required by the CSIT5930 project description.

## Features

- Crawls pages with a `breadth-first` strategy
- Starts from a seed URL and stops after `N` fetched pages
- Normalizes URLs and prevents cyclic revisits (no fragments, http/https only, lowercased scheme/host, default ports stripped)
- Skips re-fetching unchanged pages via the HTTP `If-Modified-Since` header (server returns `304 Not Modified`)
- Persistent crawl state across runs (`spider-state.ser`) — supports resuming and incremental re-crawls
- Stores both `parent -> children` and `child -> parents` relations
- Hands fetched page text to a simple file-based queue for the indexer module
- Concurrent fetch + parse pipeline (HTTP/2, configurable concurrency, batched state save)

## Repository layout

- [src/SpiderApplication.java](src/SpiderApplication.java) - CLI entry point
- [src/core/Spider.java](src/core/Spider.java) - BFS crawl loop, batched concurrent fetch + parse pipeline
- [src/core/SpiderConfig.java](src/core/SpiderConfig.java) - immutable crawler configuration
- [src/fetch/PageFetcher.java](src/fetch/PageFetcher.java) - async HTTP fetch with `If-Modified-Since`
- [src/html/HtmlDocumentParser.java](src/html/HtmlDocumentParser.java) - title, body text, and hyperlink extraction
- [src/storage/SpiderState.java](src/storage/SpiderState.java) - persistent crawl state
- [src/storage/SpiderRepository.java](src/storage/SpiderRepository.java) - serialization and exported files
- [src/index/FilePageIndexConsumer.java](src/index/FilePageIndexConsumer.java) - handoff files for the later indexer
- [scripts/build.ps1](scripts/build.ps1) - compile all Java source files under `src/`
- [scripts/run-spider.ps1](scripts/run-spider.ps1) - build and run helper script

## Requirements

- Java 20+
- PowerShell for the helper scripts on Windows

No Maven or Gradle is required.

## Build

Open a terminal in the project root and run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1
```

The script compiles every `.java` file under [src](src) and writes the `.class` files to [out](out).

## Run

### PowerShell wrapper (recommended)

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-spider.ps1 `
  -StartUrl "https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm" `
  -MaxPages 300 `
  -StorageDir "data\spider"
```

> If you see `running scripts is disabled on this system`, either keep using the `-ExecutionPolicy Bypass` form above, or enable scripts once for your user with:
> ```powershell
> Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned
> ```
> After that, `.\scripts\run-spider.ps1 ...` works directly.

Parameters:

| Parameter              | Default        | Meaning                                                                 |
|------------------------|----------------|-------------------------------------------------------------------------|
| `-StartUrl`            | (required)     | Seed URL                                                                |
| `-MaxPages`            | `300`          | Maximum number of pages to fetch                                        |
| `-StorageDir`          | `data\spider`  | Output folder (state, exports, indexer handoff)                         |
| `-SameHostOnly`        | `true`         | Restrict crawl to the seed host                                         |
| `-Concurrency`         | `8`            | Number of concurrent in-flight HTTP requests / parsers                  |
| `-SaveEvery`           | `25`           | Persist state + exports every N processed pages (final flush always)    |
| `-WritePageArtifacts`  | `false`        | Also write `pages/html/*.html` and `pages/text/*.txt` snapshots         |

### Direct Java command

```powershell
java -cp out hk.ust.csit5930.spider.SpiderApplication `
  https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm 300 data/spider true 8 25 false
```

Positional arguments (trailing ones are optional, defaults shown):

1. `startUrl` - seed URL
2. `maxPages` - max number of pages to fetch
3. `storageDir` - output folder, default `data/spider`
4. `sameHostOnly` - `true` / `false`, default `true`
5. `concurrency` - default `8`
6. `saveEvery` - default `25`
7. `writePageArtifacts` - default `false`

### Course seed URL

The project description requires crawling from:

- [https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm](https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm)

### Rerun from scratch vs. incremental

- **Fresh crawl**: delete `data\spider` (or pass a new `-StorageDir` like `data\spider-run1`).
- **Incremental rerun**: keep the same `-StorageDir`. The spider sends `If-Modified-Since` for already-fetched pages; the server replies `304` for unchanged pages, which are skipped without re-downloading the body.

## Output

By default, crawl results are written under `data\spider`.

Always produced:

- [data/spider/spider-state.ser](data/spider/spider-state.ser) - serialized crawl state (resume + dedupe)
- [data/spider/exports/pages.tsv](data/spider/exports/pages.tsv) - page metadata
- [data/spider/exports/url_to_pageid.tsv](data/spider/exports/url_to_pageid.tsv) - URL to page ID mapping
- [data/spider/exports/parent_to_children.tsv](data/spider/exports/parent_to_children.tsv) - child links
- [data/spider/exports/child_to_parents.tsv](data/spider/exports/child_to_parents.tsv) - parent links
- [data/spider/exports/frontier.txt](data/spider/exports/frontier.txt) - remaining BFS frontier
- [data/spider/indexer_input/manifest.tsv](data/spider/indexer_input/manifest.tsv) - handoff manifest for the indexer
- `data/spider/indexer_input/page-XXXX-body.txt` - extracted body text per page (one file per `pageId`)

Optional (only when `-WritePageArtifacts true`):

- `data/spider/pages/html/page-XXXX.html` - raw HTML snapshots
- `data/spider/pages/text/page-XXXX.txt` - extracted text snapshots (duplicate of indexer body)

The `data/` directory is ignored by Git, so crawl results are not committed.

## Design summary

- **BFS**: persistent frontier queue (`ArrayDeque`); each normalized URL is visited at most once.
- **Re-fetch policy**: `If-Modified-Since` on every GET for already-fetched pages; `304 Not Modified` is treated as "skip, but re-expand persisted children into the frontier".
- **Link graph**: each normalized URL has one internal `pageId`; both `parent -> children` and `child -> parents` are stored.
- **Concurrent pipeline**: each batch of up to `concurrency` URLs is fetched via `HttpClient.sendAsync` (HTTP/2), HTML is parsed on a dedicated worker pool, and results are folded back into `SpiderState` by the main thread (single writer, no locks needed).
- **Batched persistence**: state serialization + TSV export only every `saveEvery` pages (plus a final flush on exit). Avoids the previous O(N²) write cost.
- **Indexer handoff**: extracted body text is written to `indexer_input/page-XXXX-body.txt` and appended to `manifest.tsv`. The indexer module only needs these two paths.

## Performance notes

300 pages from the course seed (local benchmark on Windows, same host):

| Configuration                                                | Time    | Speedup |
|--------------------------------------------------------------|---------|---------|
| Original (serial GET, HEAD precheck, save after every page)  | 14.4 s  | 1.0x    |
| Concurrent + `If-Modified-Since` + batched save              | 3.0 s   | ~4.7x   |
| + dedicated parser pool                                      | 2.95 s  | ~4.9x   |
| + skip `pages/` artifact writes (default)                    | 2.67 s  | ~5.4x   |

Speedup is much larger on real remote sites (RTT-bound) where concurrent fetching dominates.

## How to test

### 1. Smoke test

```powershell
if (Test-Path "data\smoke") { Remove-Item "data\smoke" -Recurse -Force }
powershell -ExecutionPolicy Bypass -File .\scripts\run-spider.ps1 -StartUrl "https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm" -MaxPages 50 -StorageDir "data\smoke"
```

Expected: console ends with `Crawl finished. Indexed 50 pages.`

### 2. Full 300-page run

```powershell
if (Test-Path "data\spider") { Remove-Item "data\spider" -Recurse -Force }
powershell -ExecutionPolicy Bypass -File .\scripts\run-spider.ps1 -StartUrl "https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm" -MaxPages 300 -StorageDir "data\spider"

# True page count (excludes the manifest header line)
((Get-Content "data\spider\indexer_input\manifest.tsv" | Measure-Object -Line).Lines) - 1
```

### 3. Time it (5 runs, average)

```powershell
$times = @()
1..5 | ForEach-Object {
  if (Test-Path "data\bench") { Remove-Item "data\bench" -Recurse -Force }
  $t = Measure-Command {
    java -cp out hk.ust.csit5930.spider.SpiderApplication `
      "https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm" 300 "data\bench" true 8 25 false | Out-Null
  }
  Write-Host ("Run {0}: {1:N2} s" -f $_, $t.TotalSeconds)
  $times += $t.TotalSeconds
}
$s = $times | Measure-Object -Average -Minimum -Maximum
"avg={0:N2}s  min={1:N2}s  max={2:N2}s" -f $s.Average, $s.Minimum, $s.Maximum
```

### 4. Sweep concurrency

```powershell
foreach ($c in 1, 2, 4, 8, 16) {
  if (Test-Path "data\bench") { Remove-Item "data\bench" -Recurse -Force }
  $t = Measure-Command {
    java -cp out hk.ust.csit5930.spider.SpiderApplication `
      "https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm" 300 "data\bench" true $c 25 false | Out-Null
  }
  "concurrency={0,2}  ->  {1:N2} s" -f $c, $t.TotalSeconds
}
```

### 5. Incremental re-crawl (verifies `If-Modified-Since`)

```powershell
# Cold run
if (Test-Path "data\incr") { Remove-Item "data\incr" -Recurse -Force }
$cold = Measure-Command {
  java -cp out hk.ust.csit5930.spider.SpiderApplication `
    "https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm" 300 "data\incr" true 8 25 false | Out-Null
}
"cold = {0:N2} s" -f $cold.TotalSeconds

# Warm rerun against the same storage dir; most pages should return 304
$warm = Measure-Command {
  java -cp out hk.ust.csit5930.spider.SpiderApplication `
    "https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm" 300 "data\incr" true 8 25 false | Out-Null
}
"warm = {0:N2} s" -f $warm.TotalSeconds
```

`warm` should be noticeably faster than `cold`.

### 6. Determinism check (BFS order preserved under concurrency)

```powershell
if (Test-Path "data\det-a") { Remove-Item "data\det-a" -Recurse -Force }
if (Test-Path "data\det-b") { Remove-Item "data\det-b" -Recurse -Force }
java -cp out hk.ust.csit5930.spider.SpiderApplication "https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm" 300 "data\det-a" true 8 25 false | Out-Null
java -cp out hk.ust.csit5930.spider.SpiderApplication "https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm" 300 "data\det-b" true 8 25 false | Out-Null
fc.exe "data\det-a\exports\url_to_pageid.tsv" "data\det-b\exports\url_to_pageid.tsv"
```

`fc.exe` should print `FC: no differences encountered` — confirms `pageId` allocation order is stable.

### 7. Inspect outputs

```powershell
Get-Content "data\spider\exports\pages.tsv" | Select-Object -First 5
Get-Content "data\spider\exports\parent_to_children.tsv" | Select-Object -First 5
Get-Content "data\spider\indexer_input\manifest.tsv" | Select-Object -First 5
Get-Content "data\spider\indexer_input\page-0001-body.txt" | Select-Object -First 10
```

### 8. Cleanup benchmark / test directories

```powershell
Get-ChildItem "data\bench*","data\smoke","data\incr","data\det-*" -Directory -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force
```

## Notes for the next module

The indexer module should read [data/spider/indexer_input/manifest.tsv](data/spider/indexer_input/manifest.tsv) and the per-page body files, remove stop words, stem terms (Porter), and build the inverted files required by the search engine.
