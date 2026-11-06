package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.JsonLdParser;
import com.pricetracker.scraper.JsonLdStoreAdapter;
import com.pricetracker.scraper.ScrapedProduct;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

// Xtreme has full JSON-LD with EAN; its "sku" is the maker's model code, e.g. "SA400S37/480G"
@Component
public class XtremeAdapter extends JsonLdStoreAdapter {

    public XtremeAdapter(JsonLdParser jsonLdParser) {
        super(jsonLdParser);
    }

    @Override
    public String storeCode() {
        return "xtreme";
    }

    @Override
    public String sitemapUrl() {
        return "https://xtreme.bg/sitemap";
    }

    // The index also lists brands, categories, blogs and pages
    @Override
    public boolean isProductSitemap(String sitemapUrl) {
        return sitemapUrl.contains("/sitemap/products/");
    }

    @Override
    protected void customize(Document page, ScrapedProduct product) {
        if (product.getModelCode() == null) {
            product.setModelCode(product.getStoreSku());
        }
    }
}
