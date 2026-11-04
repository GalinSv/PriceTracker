package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.JsonLdParser;
import com.pricetracker.scraper.JsonLdStoreAdapter;
import org.springframework.stereotype.Component;

// Muziker has full JSON-LD. Its sitemaps mix products with categories, brands and blog posts.
@Component
public class MuzikerAdapter extends JsonLdStoreAdapter {

    public MuzikerAdapter(JsonLdParser jsonLdParser) {
        super(jsonLdParser);
    }

    @Override
    public String storeCode() {
        return "muziker";
    }

    @Override
    public String sitemapUrl() {
        return "https://www.muziker.bg/sitemap.xml";
    }

    // Only product entries have a picture from /uploads/products/
    @Override
    public boolean isProductEntry(String url, String entryXml) {
        return entryXml.contains("/uploads/products/");
    }
}
