package com.pricetracker.scraper;

import org.jsoup.nodes.Document;

// One implementation per store: knows how to read that store's product page
public interface StoreAdapter {

    // Must match Store.code in the database
    String storeCode();

    ScrapedProduct parseProduct(Document page);

    // The sitemap (or sitemap index) that lists the store's products.
    // Stores without an index can give their robots.txt, which lists the sitemaps.
    // null = the store's products can't be searched by word yet.
    default String sitemapUrl() {
        return null;
    }

    // A sitemap index can also list categories, blog posts etc.
    default boolean isProductSitemap(String sitemapUrl) {
        return true;
    }

    default boolean isProductUrl(String url) {
        return true;
    }

    // For sitemaps where only the whole <url> entry (images etc.) tells products from categories
    default boolean isProductEntry(String url, String entryXml) {
        return isProductUrl(url);
    }
}
