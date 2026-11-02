package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.HtmlUtils;
import com.pricetracker.scraper.PriceParser;
import com.pricetracker.scraper.ScrapedProduct;
import com.pricetracker.scraper.StoreAdapter;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ozone has no JSON-LD product data, so we read the HTML
@Component
public class OzoneAdapter implements StoreAdapter {

    // Ozone's own product code from its tracking script: "content_id":"MMHP0000288"
    private static final Pattern STORE_SKU = Pattern.compile("\"content_id\":\"([^\"]+)\"");

    @Override
    public String storeCode() {
        return "ozone";
    }

    @Override
    public String sitemapUrl() {
        return "https://www.ozone.bg/sitemap_index.xml";
    }

    @Override
    public boolean isProductSitemap(String sitemapUrl) {
        return sitemapUrl.contains("/sitemap-products-");
    }

    @Override
    public boolean isProductUrl(String url) {
        return url.contains("/product/");
    }

    @Override
    public ScrapedProduct parseProduct(Document page) {
        ScrapedProduct product = new ScrapedProduct();
        String ogTitle = HtmlUtils.meta(page, "og:title");
        product.setTitle(ogTitle != null ? ogTitle : HtmlUtils.text(page.selectFirst("h1")));
        product.setImageUrl(HtmlUtils.meta(page, "og:image"));

        // Rows of the "specifications" table
        product.setBrand(tableValue(page, "Марка"));
        product.setModelCode(tableValue(page, "Каталожен номер"));
        product.setEan(tableValue(page, "Баркод"));

        product.setPrice(PriceParser.parse(mainPriceText(page)));
        product.setCurrency("EUR");
        product.setInStock(isInStock(page));

        Matcher sku = STORE_SKU.matcher(page.html());
        if (sku.find()) {
            product.setStoreSku(sku.group(1));
        }
        return product;
    }

    // The page also shows prices of other products, so find the main one by its id:
    private String mainPriceText(Document page) {
        Element productId = page.selectFirst("#product_addtocart_form input[name=product]");
        if (productId == null) {
            return null;
        }
        return HtmlUtils.text(page.getElementById("product-price-" + productId.val()));
    }

    // <p class="availability out-of-stock" id="availability-holder">Изчерпан</p>
    private Boolean isInStock(Document page) {
        Element availability = page.getElementById("availability-holder");
        if (availability == null) {
            return null;
        }
        return !availability.hasClass("out-of-stock");
    }

    // <tr><th>Баркод</th><td>6923410710205</td></tr> -> "6923410710205"
    private String tableValue(Document page, String label) {
        Element header = page.selectFirst("th:containsOwn(" + label + ")");
        return header == null ? null : HtmlUtils.text(header.nextElementSibling());
    }
}
