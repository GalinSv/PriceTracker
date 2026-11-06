package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.JsonLdParser;
import com.pricetracker.scraper.JsonLdStoreAdapter;
import org.springframework.stereotype.Component;

import java.util.Set;

// VsichkiStoki has JSON-LD with name, brand, price and stock
@Component
public class VsichkiStokiAdapter extends JsonLdStoreAdapter {

    private static final Set<String> INFO_PAGES = Set.of("faq.html", "about-us.html", "contact-information.html",
            "delivery.html", "gdp-privacy-policy.html", "about-cookies.html", "news.html");

    public VsichkiStokiAdapter(JsonLdParser jsonLdParser) {
        super(jsonLdParser);
    }

    @Override
    public String storeCode() {
        return "vsichkistoki";
    }

    @Override
    public String sitemapUrl() {
        return "https://vsichkistoki.bg/sitemap.xml";
    }

    // Products are https://vsichkistoki.bg/detska-igra-morski-ribolov.html. The "...-details-N.html" and
    // "product-product-id-N.html" links are categories, articles and old copies of product pages.
    @Override
    public boolean isProductUrl(String url) {
        String page = url.substring(url.lastIndexOf('/') + 1);
        return page.endsWith(".html") && !page.contains("-details-") && !page.contains("product-id")
                && !INFO_PAGES.contains(page);
    }
}
