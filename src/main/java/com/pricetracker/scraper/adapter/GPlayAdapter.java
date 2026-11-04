package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.JsonLdParser;
import com.pricetracker.scraper.JsonLdStoreAdapter;
import com.pricetracker.scraper.ScrapedProduct;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// GPlay has JSON-LD without mpn or EAN. Its own code is "brand-category-model",
// e.g. "KIN-RAM-KVR26N19S6-4" or "LOGITECH-MOUSE-G305", so the model is taken from there.
@Component
public class GPlayAdapter extends JsonLdStoreAdapter {

    private static final Pattern MODEL_IN_SKU = Pattern.compile("^[^-]+-[^-]+-(.+)$");

    public GPlayAdapter(JsonLdParser jsonLdParser) {
        super(jsonLdParser);
    }

    @Override
    public String storeCode() {
        return "gplay";
    }

    // Also lists about a hundred category pages, which can't be told apart by the link
    @Override
    public String sitemapUrl() {
        return "https://gplay.bg/sitemap.xml";
    }

    @Override
    protected void customize(Document page, ScrapedProduct product) {
        if (product.getModelCode() == null && product.getStoreSku() != null) {
            Matcher matcher = MODEL_IN_SKU.matcher(product.getStoreSku());
            if (matcher.matches()) {
                product.setModelCode(matcher.group(1));
            }
        }
    }
}
