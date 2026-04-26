package hk.ust.csit5930.spider;

import hk.ust.csit5930.spider.core.Spider;
import hk.ust.csit5930.spider.core.SpiderConfig;
import hk.ust.csit5930.spider.index.FilePageIndexConsumer;
import hk.ust.csit5930.spider.storage.SpiderRepository;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class SpiderApplication {
    private SpiderApplication() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            printUsage();
            return;
        }

        // Input example:
        /**
         * java -cp out hk.ust.csit5930.spider.SpiderApplication
         * https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm   <- startUrl
         * 300                                                      <- maxPages
         * data/spider                                              <- storageDir   (optional, default: data/spider)
         * true                                                     <- sameHostOnly (optional, default: true)
         * 8                                                        <- concurrency       (optional, default: 8)
         * 25                                                       <- saveEvery         (optional, default: 25)
         * false                                                    <- writePageArtifacts (optional, default: false)
         */
        String startUrl = args[0];
        int maxPages = Integer.parseInt(args[1]);
        Path storageDir = args.length >= 3
            ? Paths.get(args[2]).toAbsolutePath().normalize()
            : Paths.get("data", "spider").toAbsolutePath().normalize();
        boolean sameHostOnly = args.length < 4 || Boolean.parseBoolean(args[3]);
        int concurrency = args.length >= 5 ? Integer.parseInt(args[4]) : 8;
        int saveEvery = args.length >= 6 ? Integer.parseInt(args[5]) : 25;
        boolean writePageArtifacts = args.length >= 7 && Boolean.parseBoolean(args[6]);

        // Create a record for the spider configuration, which is an immutable data holder
        SpiderConfig config = new SpiderConfig(
            startUrl,
            maxPages,
            storageDir,
            sameHostOnly,
            15,
            "CSIT5930Spider/1.0 (+https://github.com/)",            // Default ID of the crawler, can be customized
            concurrency,
            saveEvery,
            writePageArtifacts
        );

        // Create the repository and index consumer, then start the spider
        SpiderRepository repository = new SpiderRepository(storageDir, writePageArtifacts);
        FilePageIndexConsumer indexConsumer = new FilePageIndexConsumer(storageDir.resolve("indexer_input"));
        Spider spider = new Spider(config, repository, indexConsumer);
        // Start to crawl with the given configuration, repository and index consumer
        spider.crawl();
    }

    private static void printUsage() {
        System.out.println("Usage: java -cp out hk.ust.csit5930.spider.SpiderApplication <startUrl> <maxPages> [storageDir] [sameHostOnly] [concurrency] [saveEvery] [writePageArtifacts]");
        System.out.println("Example: java -cp out hk.ust.csit5930.spider.SpiderApplication https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm 300 data/spider true 8 25 false");
    }
}
