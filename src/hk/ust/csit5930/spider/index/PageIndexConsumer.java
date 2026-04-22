package hk.ust.csit5930.spider.index;

import hk.ust.csit5930.spider.html.HtmlDocument;
import hk.ust.csit5930.spider.model.PageInfo;

import java.io.IOException;

public interface PageIndexConsumer {
    void index(PageInfo pageInfo, HtmlDocument htmlDocument) throws IOException;
}
