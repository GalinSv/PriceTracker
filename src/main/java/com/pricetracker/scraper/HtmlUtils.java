package com.pricetracker.scraper;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public final class HtmlUtils {

    private HtmlUtils() {
    }


    public static String meta(Document page, String property) {
        Element meta = page.selectFirst("meta[property=" + property + "]");
        return meta == null ? null : blankToNull(meta.attr("content"));
    }

    public static String text(Element element) {
        return element == null ? null : blankToNull(element.text());
    }

    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
