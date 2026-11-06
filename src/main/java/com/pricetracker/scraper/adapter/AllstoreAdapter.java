package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.MicrodataStoreAdapter;
import org.springframework.stereotype.Component;

// Allstore has schema.org microdata with EAN and model code
@Component
public class AllstoreAdapter extends MicrodataStoreAdapter {

    @Override
    public String storeCode() {
        return "allstore";
    }

    // The normal sitemap mixes products with categories and blog posts; the picture sitemap is mostly products
    @Override
    public String sitemapUrl() {
        return "https://allstore.bg/sitemap-image.xml";
    }

    // The picture sitemap also has the home page and about 200 category pages (/category/5745/byura.html)
    @Override
    public boolean isProductUrl(String url) {
        return !url.equals("https://allstore.bg/") && !url.contains("/category/");
    }
}
