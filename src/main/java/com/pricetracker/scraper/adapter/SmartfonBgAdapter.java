package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.HtmlUtils;
import com.pricetracker.scraper.PriceParser;
import com.pricetracker.scraper.ScrapedProduct;
import com.pricetracker.scraper.StoreAdapter;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// SmartfonBG (OpenCart) has no product data for machines, so we read the HTML.
// Prices are written as "740.86€ (1,449.00лв.)". There is no EAN or model code.
@Component
public class SmartfonBgAdapter implements StoreAdapter {

    private static final Pattern EURO_PRICE = Pattern.compile("([0-9][0-9.,]*)\\s*€");

    @Override
    public String storeCode() {
        return "smartfonbg";
    }

    // Every link in it is a product, with the product name as image:title
    @Override
    public String sitemapUrl() {
        return "https://smartfonbg.com/sitemap.xml";
    }

    @Override
    public ScrapedProduct parseProduct(Document page) {
        ScrapedProduct product = new ScrapedProduct();
        product.setTitle(HtmlUtils.text(page.selectFirst("h1")));

        // A discount is in product_special, the normal price in product_price
        BigDecimal special = euroPrice(page.selectFirst("input[name=product_special]"));
        product.setPrice(special != null ? special : euroPrice(page.selectFirst("input[name=product_price]")));
        product.setCurrency("EUR");

        // <span class="instock-span">Изчерпан</span> when sold out
        String stock = HtmlUtils.text(page.selectFirst("span.instock-span"));
        product.setInStock(stock == null ? null : !stock.equalsIgnoreCase("Изчерпан"));

        Element image = page.selectFirst("a.thumbnail[href]");
        product.setImageUrl(image != null ? image.absUrl("href") : HtmlUtils.meta(page, "og:image"));
        return product;
    }

    private BigDecimal euroPrice(Element input) {
        if (input == null) {
            return null;
        }
        Matcher matcher = EURO_PRICE.matcher(input.attr("value"));
        return matcher.find() ? PriceParser.parse(matcher.group(1)) : null;
    }
}
