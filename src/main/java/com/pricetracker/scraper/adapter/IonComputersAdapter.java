package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.JsonLdParser;
import com.pricetracker.scraper.JsonLdStoreAdapter;
import org.springframework.stereotype.Component;

// ION Computers has full JSON-LD; its mpn is the manufacturer's part number
@Component
public class IonComputersAdapter extends JsonLdStoreAdapter {

    public IonComputersAdapter(JsonLdParser jsonLdParser) {
        super(jsonLdParser);
    }

    @Override
    public String storeCode() {
        return "ioncomputers";
    }

    // The name comes from robots.txt and has a date in it, so it may change
    @Override
    public String sitemapUrl() {
        return "https://www.ioncomputers.bg/sitemap_main_062026.xml";
    }

    @Override
    public boolean isProductUrl(String url) {
        return url.contains("/product/");
    }
}
