package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.HtmlUtils;
import com.pricetracker.scraper.JsonLdParser;
import com.pricetracker.scraper.JsonLdStoreAdapter;
import com.pricetracker.scraper.PriceParser;
import com.pricetracker.scraper.ScrapedProduct;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

// ExpressLink (WooCommerce). Its JSON-LD has the name and the stock but no price,
// so the price is read from the page: <p class="price"> under the title
@Component
public class ExpressLinkAdapter extends JsonLdStoreAdapter {

    public ExpressLinkAdapter(JsonLdParser jsonLdParser) {
        super(jsonLdParser);
    }

    @Override
    public String storeCode() {
        return "expresslink";
    }

    @Override
    public String sitemapUrl() {
        return "https://expresslink.bg/wp-sitemap.xml";
    }

    // The index also lists posts, pages, categories and users
    @Override
    public boolean isProductSitemap(String sitemapUrl) {
        return sitemapUrl.contains("-posts-product-");
    }

    @Override
    protected void customize(Document page, ScrapedProduct product) {
        Element price = page.selectFirst("h1 ~ p.price");
        if (price == null) {
            return;
        }
        // A discount shows the old price in <del> and the new one in <ins>
        Element current = price.selectFirst("ins .woocommerce-Price-amount");
        if (current == null) {
            current = price.selectFirst(".woocommerce-Price-amount");
        }
        product.setPrice(PriceParser.parse(HtmlUtils.text(current)));
        product.setCurrency("EUR");
    }
}
