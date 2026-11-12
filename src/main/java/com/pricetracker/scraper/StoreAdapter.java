package com.pricetracker.scraper;

import org.jsoup.nodes.Document;

import java.util.List;
import java.util.Map;

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

    // Stores without a usable sitemap: the products are read from the category pages instead.
    // The page that links to the categories; null = the store has a sitemap (or can't be searched).
    default String categoriesUrl() {
        return null;
    }

    // The categories to read, from the categoriesUrl page
    default List<String> categoryUrls(Document categoriesPage) {
        return List.of();
    }

    // The pages after the first one of a category
    default List<String> morePages(String categoryUrl, Document firstPage) {
        return List.of();
    }

    // Product link -> product name (or null), from one page of a category
    default Map<String, String> productLinks(Document categoryPage) {
        return Map.of();
    }

    // Links with the same key are one product, e.g. one product listed in two categories
    default String productKey(String url) {
        return url;
    }
}
