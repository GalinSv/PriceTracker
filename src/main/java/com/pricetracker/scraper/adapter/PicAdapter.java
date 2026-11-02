package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.HtmlUtils;
import com.pricetracker.scraper.JsonLdParser;
import com.pricetracker.scraper.JsonLdStoreAdapter;
import com.pricetracker.scraper.ScrapedProduct;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

// PIC has full JSON-LD with EAN, but cuts long names with "..."
@Component
public class PicAdapter extends JsonLdStoreAdapter {

    private static final int MAX_DESCRIPTION_AS_TITLE = 200;
    private static final Pattern PRODUCT_URL = Pattern.compile("https://www\\.pic\\.bg/[a-z0-9-]+-\\d+");

    public PicAdapter(JsonLdParser jsonLdParser) {
        super(jsonLdParser);
    }

    @Override
    public String storeCode() {
        return "pic";
    }

    @Override
    public String sitemapUrl() {
        return "https://www.pic.bg/sitemap.xml";
    }

    @Override
    public boolean isProductUrl(String url) {
        return PRODUCT_URL.matcher(url).matches();
    }

    @Override
    protected void customize(Document page, ScrapedProduct product) {
        // The short og:description holds the full name, e.g. "be quiet! ... POWER ZONE 2 1200W"
        String description = HtmlUtils.meta(page, "og:description");
        if (product.getTitle() != null && product.getTitle().endsWith("...")
                && description != null && description.length() <= MAX_DESCRIPTION_AS_TITLE) {
            product.setTitle(description);
        }
    }
}
