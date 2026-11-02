package com.pricetracker.scraper;

import com.pricetracker.exception.ScrapingException;
import org.jsoup.nodes.Document;

public abstract class JsonLdStoreAdapter implements StoreAdapter {

    private final JsonLdParser jsonLdParser;

    protected JsonLdStoreAdapter(JsonLdParser jsonLdParser) {
        this.jsonLdParser = jsonLdParser;
    }

    @Override
    public ScrapedProduct parseProduct(Document page) {
        ScrapedProduct product = jsonLdParser.findProduct(page)
                .map(jsonLdParser::toScrapedProduct)
                .orElseThrow(() -> new ScrapingException("No product data (JSON-LD) found on the page"));

        // og:image is usually the large picture made for sharing; JSON-LD images can be thumbnails
        String ogImage = HtmlUtils.meta(page, "og:image");
        if (ogImage != null) {
            product.setImageUrl(ogImage);
        }

        customize(page, product);
        return product;
    }

    protected void customize(Document page, ScrapedProduct product) {
    }
}
