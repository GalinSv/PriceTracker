package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.HtmlUtils;
import com.pricetracker.scraper.JsonLdParser;
import com.pricetracker.scraper.JsonLdStoreAdapter;
import com.pricetracker.scraper.ScrapedProduct;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Dice's JSON-LD has the price, but no brand or stock, and its "mpn" is the store's own code.
// The rest is in the description: "Производител: Anker", "Фабричен номер: A3936G11", "EAN номер: 194644106171".
@Component
public class DiceAdapter extends JsonLdStoreAdapter {

    private static final Pattern BRAND = Pattern.compile("Производител:\\s*([^<]+)<");
    private static final Pattern MODEL_CODE = Pattern.compile("Фабричен номер:\\s*([^<]+)<");
    private static final Pattern EAN = Pattern.compile("EAN номер:\\s*(\\d{8,14})");

    public DiceAdapter(JsonLdParser jsonLdParser) {
        super(jsonLdParser);
    }

    @Override
    public String storeCode() {
        return "dice";
    }

    @Override
    public String sitemapUrl() {
        return "https://dice.bg/sitemap_product.xml";
    }

    @Override
    protected void customize(Document page, ScrapedProduct product) {
        String html = page.html();
        product.setBrand(find(BRAND, html));
        product.setModelCode(find(MODEL_CODE, html));
        product.addEans(find(EAN, page.text()));

        // Products that can't be bought show a "notify me" button instead of "add to cart"
        product.setInStock(page.getElementById("product-addtocart-button") != null);
    }

    private String find(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return HtmlUtils.blankToNull(Parser.unescapeEntities(matcher.group(1), false).replace(' ', ' '));
    }
}
