package hk.ust.csit5930.spider.html;

import hk.ust.csit5930.spider.util.UrlNormalizer;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class HtmlDocumentParser {
    private final UrlNormalizer urlNormalizer;

    public HtmlDocumentParser(UrlNormalizer urlNormalizer) {
        this.urlNormalizer = urlNormalizer;
    }

    public HtmlDocument parse(String pageUrl, String html) throws IOException {
        URI baseUri = URI.create(pageUrl);
        LinkedHashSet<String> links = new LinkedHashSet<>();
        StringBuilder titleBuilder = new StringBuilder();
        StringBuilder bodyBuilder = new StringBuilder();

        HTMLEditorKit.ParserCallback callback = new HTMLEditorKit.ParserCallback() {
            private boolean insideTitle;
            private boolean insideScript;
            private boolean insideStyle;

            @Override
            public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int pos) {
                if (tag == HTML.Tag.TITLE) {
                    insideTitle = true;
                } else if (tag == HTML.Tag.SCRIPT) {
                    insideScript = true;
                } else if (tag == HTML.Tag.STYLE) {
                    insideStyle = true;
                } else if (tag == HTML.Tag.A) {
                    Object href = attributes.getAttribute(HTML.Attribute.HREF);
                    if (href != null) {
                        urlNormalizer.normalize(href.toString(), baseUri).ifPresent(links::add);
                    }
                }
            }

            @Override
            public void handleEndTag(HTML.Tag tag, int pos) {
                if (tag == HTML.Tag.TITLE) {
                    insideTitle = false;
                } else if (tag == HTML.Tag.SCRIPT) {
                    insideScript = false;
                } else if (tag == HTML.Tag.STYLE) {
                    insideStyle = false;
                }
            }

            @Override
            public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int pos) {
                if (tag == HTML.Tag.A) {
                    Object href = attributes.getAttribute(HTML.Attribute.HREF);
                    if (href != null) {
                        urlNormalizer.normalize(href.toString(), baseUri).ifPresent(links::add);
                    }
                }
            }

            @Override
            public void handleText(char[] data, int pos) {
                if (insideScript || insideStyle) {
                    return;
                }
                String text = new String(data).trim();
                if (text.isEmpty()) {
                    return;
                }
                if (insideTitle) {
                    if (titleBuilder.length() > 0) {
                        titleBuilder.append(' ');
                    }
                    titleBuilder.append(text);
                    return;
                }
                if (bodyBuilder.length() > 0) {
                    bodyBuilder.append(System.lineSeparator());
                }
                bodyBuilder.append(text);
            }
        };

        try (Reader reader = new StringReader(html)) {
            new ParserDelegator().parse(reader, callback, true);
        }

        return new HtmlDocument(
            titleBuilder.toString().trim(),
            bodyBuilder.toString().trim(),
            new ArrayList<>(links)
        );
    }
}
