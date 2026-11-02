package com.pricetracker.scraper;

import org.jsoup.nodes.Document;

// One implementation per store: knows how to read that store's product page
public interface StoreAdapter {

    // Must match Store.code in the database
    String storeCode();

    ScrapedProduct parseProduct(Document page);
}
