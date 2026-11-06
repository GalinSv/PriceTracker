package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.JsonLdParser;
import com.pricetracker.scraper.JsonLdStoreAdapter;
import com.pricetracker.scraper.ScrapedProduct;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

// BestPC's JSON-LD has name, brand, price and stock; its "sku" is only the store's own number.
// Its "mpn" is the model code, e.g. "910-001794", or for some products one or more barcodes.
@Component
public class BestPcAdapter extends JsonLdStoreAdapter {

    // https://bestpc.bg/bg/mishki-mouses/razer/razer-goliathus-extended-mouse-pad-control-69463
    // = category/brand/name-number; category/brand alone is a list page
    private static final Pattern PRODUCT_URL = Pattern.compile("https://bestpc\\.bg/bg/[^/]+/[^/]+/[^/]+-\\d+");

    public BestPcAdapter(JsonLdParser jsonLdParser) {
        super(jsonLdParser);
    }

    @Override
    public String storeCode() {
        return "bestpc";
    }

    @Override
    public String sitemapUrl() {
        return "https://bestpc.bg/sitemap.xml";
    }

    @Override
    public boolean isProductUrl(String url) {
        return PRODUCT_URL.matcher(url).matches();
    }

    @Override
    protected void customize(Document page, ScrapedProduct product) {
        product.moveBarcodesOutOfModelCode();
    }
}
