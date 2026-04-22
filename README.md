# CSIT5930 Spider Module

This repository contains a Java implementation of the `spider` required by the CSIT5930 project description.

## Features

- Crawls pages with a `breadth-first` strategy
- Starts from a seed URL and stops after `N` fetched pages
- Normalizes URLs and prevents cyclic revisits
- Checks `Last-Modified` before re-fetching an already indexed page
- Stores a persistent `url -> pageId` mapping
- Stores both `parent -> children` and `child -> parents` relations
- Saves HTML and extracted text snapshots locally
- Exports flat files for inspection
- Hands fetched page text to a simple file-based indexing input queue

## Repository layout

- [src/SpiderApplication.java](src/SpiderApplication.java) - CLI entry point
- [src/core/Spider.java](src/core/Spider.java) - BFS crawl loop and crawl decisions
- [src/core/SpiderConfig.java](src/core/SpiderConfig.java) - immutable crawler configuration
- [src/fetch/PageFetcher.java](src/fetch/PageFetcher.java) - HTTP fetch and `Last-Modified` checks
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

Open a terminal in [CSIT5930_project](CSIT5930_project) and run:


`powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1`

The script compiles every `.java` file under [src](src) and writes the `.class` files to [out](out).

## Run

### Direct Java command

Recommended command:

`java -cp out hk.ust.csit5930.spider.SpiderApplication https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm 300 data/spider true`

Arguments:

1. `startUrl` - seed URL
2. `maxPages` - maximum number of pages to fetch
3. `storageDir` - optional output folder, default `data/spider`
4. `sameHostOnly` - optional `true` or `false`, default `true`

### PowerShell wrapper

You can also run:

`powershell -ExecutionPolicy Bypass -File .\scripts\run-spider.ps1 -StartUrl "https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm" -MaxPages 300 -StorageDir "data\spider" -SameHostOnly true`

If you want a fresh crawl without reusing old state, use a new output directory each time, for example:

- `data/spider-run1`
- `data/spider-run2`

Example:

`powershell -ExecutionPolicy Bypass -File .\scripts\run-spider.ps1 -StartUrl "https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm" -MaxPages 300 -StorageDir "data\spider-run1" -SameHostOnly true`

### Course seed URL

The project description requires crawling from:

- [https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm](https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm)

## Output

By default, crawl results are written under [CSIT5930_project/data/spider](CSIT5930_project/data/spider).

Important outputs:

- [CSIT5930_project/data/spider/pages/html](CSIT5930_project/data/spider/pages/html) - raw HTML snapshots
- [CSIT5930_project/data/spider/pages/text](CSIT5930_project/data/spider/pages/text) - extracted page text
- [CSIT5930_project/data/spider/exports/pages.tsv](CSIT5930_project/data/spider/exports/pages.tsv) - page metadata
- [CSIT5930_project/data/spider/exports/url_to_pageid.tsv](CSIT5930_project/data/spider/exports/url_to_pageid.tsv) - URL to page ID mapping
- [CSIT5930_project/data/spider/exports/parent_to_children.tsv](CSIT5930_project/data/spider/exports/parent_to_children.tsv) - child links
- [CSIT5930_project/data/spider/exports/child_to_parents.tsv](CSIT5930_project/data/spider/exports/child_to_parents.tsv) - parent links
- [CSIT5930_project/data/spider/indexer_input/manifest.tsv](CSIT5930_project/data/spider/indexer_input/manifest.tsv) - handoff manifest for the next module

The `data/` directory is ignored by Git, so crawl results will not be committed by default.

## Rerun from scratch

Either delete [data/spider](data/spider) and run again, or choose a new output directory such as [data/spider-run1](data/spider-run1) or [data/spider-run2](data/spider-run2).

## Design summary

- `BFS`: the spider stores a persistent frontier queue and visits each normalized URL once.
- `Re-fetch policy`: the spider sends `HEAD` requests and only re-fetches pages when remote `Last-Modified` is newer.
- `Link graph`: each normalized URL gets one internal `pageId`, with both parent-to-child and child-to-parent relations stored.
- `Indexer handoff`: extracted page text is written into `indexer_input/` so the next module can build the inverted index.

## Notes

For the full project, the next module should read `indexer_input/manifest.tsv` and the body files, remove stop words, stem terms, and build the inverted files required by the search engine.