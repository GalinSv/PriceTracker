package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.JsonLdParser;
import com.pricetracker.scraper.JsonLdStoreAdapter;
import org.springframework.stereotype.Component;

// Techmart has full JSON-LD with EAN
@Component
public class TechmartAdapter extends JsonLdStoreAdapter {

    public TechmartAdapter(JsonLdParser jsonLdParser) {
        super(jsonLdParser);
    }

    @Override
    public String storeCode() {
        return "techmart";
    }

    @Override
    public String sitemapUrl() {
        return "https://techmart.bg/sitemap.xml";
    }

    @Override
    public boolean isProductSitemap(String sitemapUrl) {
        return sitemapUrl.contains("sitemap-products");
    }
}
