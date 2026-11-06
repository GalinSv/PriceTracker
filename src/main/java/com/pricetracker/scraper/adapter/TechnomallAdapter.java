package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.JsonLdParser;
import com.pricetracker.scraper.JsonLdStoreAdapter;
import org.springframework.stereotype.Component;

// Technomall has full JSON-LD with EAN
@Component
public class TechnomallAdapter extends JsonLdStoreAdapter {

    public TechnomallAdapter(JsonLdParser jsonLdParser) {
        super(jsonLdParser);
    }

    @Override
    public String storeCode() {
        return "technomall";
    }

    @Override
    public String sitemapUrl() {
        return "https://www.technomall.bg/sitemap.xml";
    }

    // The sitemap also has /category/, /brand/ and blog pages
    @Override
    public boolean isProductUrl(String url) {
        return url.contains("/product/");
    }
}
