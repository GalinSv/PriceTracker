package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.HtmlUtils;
import com.pricetracker.scraper.JsonLdParser;
import com.pricetracker.scraper.JsonLdStoreAdapter;
import com.pricetracker.scraper.PriceParser;
import com.pricetracker.scraper.ScrapedProduct;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

// TImobile (WooCommerce). Its JSON-LD price is the price before discount and is marked as BGN although
// it is in euro, so the price comes from <meta property="product:price:amount">, which is the real one.
// The "sku" is the maker's model code, e.g. "MJRQ4ZD/A", or for some products the barcode.
@Component
public class TiMobileAdapter extends JsonLdStoreAdapter {

    public TiMobileAdapter(JsonLdParser jsonLdParser) {
        super(jsonLdParser);
    }

    @Override
    public String storeCode() {
        return "timobile";
    }

    @Override
    public String sitemapUrl() {
        return "https://www.timobile.bg/sitemap_index.xml";
    }

    @Override
    public boolean isProductSitemap(String sitemapUrl) {
        return sitemapUrl.contains("/product-sitemap");
    }

    @Override
    protected void customize(Document page, ScrapedProduct product) {
        BigDecimal price = PriceParser.parse(HtmlUtils.meta(page, "product:price:amount"));
        if (price != null) {
            product.setPrice(price);
            product.setCurrency(HtmlUtils.meta(page, "product:price:currency"));
        }
        // "b2b-59717" is the store's own number, not a model code
        String sku = product.getStoreSku();
        if (product.getModelCode() == null && sku != null && !sku.startsWith("b2b-")) {
            product.setModelCode(sku);
        }
        product.moveBarcodesOutOfModelCode();
    }
}
