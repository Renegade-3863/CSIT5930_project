package hk.ust.csit5930.spider.html;

import java.util.List;

public record HtmlDocument(String title, String bodyText, List<String> links) {
}
