package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.JsonLdParser;
import com.pricetracker.scraper.JsonLdStoreAdapter;
import com.pricetracker.scraper.ScrapedProduct;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Pattern;

// Technomarket's JSON-LD has name, brand and price, but no stock, EAN or model code.
// The EAN is in a widget: <tm-pointandplace ean="5099206129849" brand="LOGITECH">.
@Component
public class TechnomarketAdapter extends JsonLdStoreAdapter {

    // https://www.technomarket.bg/mishki/logitech-g700-gaming-mouse-910-001761-09097673
    private static final Pattern PRODUCT_URL = Pattern.compile("https://www\\.technomarket\\.bg/[^/]+/[^/]+-\\d{6,}");

    public TechnomarketAdapter(JsonLdParser jsonLdParser) {
        super(jsonLdParser);
    }

    @Override
    public String storeCode() {
        return "technomarket";
    }

    @Override
    public String sitemapUrl() {
        return "https://www.technomarket.bg/sitemap.xml";
    }

    @Override
    public boolean isProductUrl(String url) {
        return PRODUCT_URL.matcher(url).matches();
    }

    @Override
    protected void customize(Document page, ScrapedProduct product) {
        Element widget = page.selectFirst("tm-pointandplace[ean]");
        if (widget != null) {
            product.addEans(widget.attr("ean"));
        }

        // Pages of removed products are still there, with price 0
        if (product.getPrice() != null && product.getPrice().compareTo(BigDecimal.ZERO) == 0) {
            product.setPrice(null);
        }

        // The main product's block has "add to cart", or "Изчерпан" when sold out
        Element action = page.selectFirst("div.action[data-product=" + product.getStoreSku() + "]");
        if (action != null) {
            product.setInStock(action.selectFirst("[data-action=addCart]") != null);
        }
    }
}
