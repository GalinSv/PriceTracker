package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.JsonLdParser;
import com.pricetracker.scraper.JsonLdStoreAdapter;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

// JAR Computers has full JSON-LD with brand and mpn
@Component
public class JarComputersAdapter extends JsonLdStoreAdapter {

    // "-prod-" are the store's own products, "-sprod-" are suppliers' products, which also have a price
    private static final Pattern PRODUCT_URL = Pattern.compile("-s?prod-[^/]+\\.html$");

    public JarComputersAdapter(JsonLdParser jsonLdParser) {
        super(jsonLdParser);
    }

    @Override
    public String storeCode() {
        return "jarcomputers";
    }

    // There is no sitemap index: robots.txt lists sitemap-1.xml.gz, sitemap-2.xml.gz ...
    @Override
    public String sitemapUrl() {
        return "https://www.jarcomputers.com/robots.txt";
    }

    // special.xml.gz has only categories, brands and searches
    @Override
    public boolean isProductSitemap(String sitemapUrl) {
        return !sitemapUrl.contains("/special.");
    }

    @Override
    public boolean isProductUrl(String url) {
        return PRODUCT_URL.matcher(url).find();
    }
}
