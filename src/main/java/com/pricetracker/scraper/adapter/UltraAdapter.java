package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.HtmlUtils;
import com.pricetracker.scraper.PriceParser;
import com.pricetracker.scraper.ScrapedProduct;
import com.pricetracker.scraper.StoreAdapter;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

// Ultra has no JSON-LD product data, so we read the HTML. There is no model code, only EAN.
@Component
public class UltraAdapter implements StoreAdapter {

    @Override
    public String storeCode() {
        return "ultra";
    }

    @Override
    public String sitemapUrl() {
        return "https://www.ultra.bg/sitemap.xml";
    }

    // The sitemaps mix products with categories; only products have pictures from /media/catalog/product/
    @Override
    public boolean isProductEntry(String url, String entryXml) {
        return entryXml.contains("/media/catalog/product/");
    }

    @Override
    public ScrapedProduct parseProduct(Document page) {
        ScrapedProduct product = new ScrapedProduct();
        String name = HtmlUtils.text(page.selectFirst("h1 [itemprop=name]"));
        product.setTitle(name != null ? name : HtmlUtils.meta(page, "og:title"));
        product.setImageUrl(imageUrl(page));

        // Rows of the "specifications" table
        product.setBrand(tableValue(page, "Производител"));
        product.setEan(tableValue(page, "EAN"));

        product.setPrice(PriceParser.parse(HtmlUtils.meta(page, "product:price:amount")));
        product.setCurrency(HtmlUtils.meta(page, "product:price:currency"));
        product.setInStock(isInStock(page));
        return product;
    }

    // og:image is a 265x265 thumbnail, the gallery has the 700x700 picture
    private String imageUrl(Document page) {
        Element image = page.selectFirst("img[itemprop=image]");
        String src = image == null ? null : HtmlUtils.blankToNull(image.absUrl("src"));
        return src != null ? src : HtmlUtils.meta(page, "og:image");
    }

    // <p title="Наличност" class="... available ...">В наличност</p>, or "unavailable" when sold out
    private Boolean isInStock(Document page) {
        Element availability = page.selectFirst("p[title=Наличност]");
        if (availability == null) {
            return null;
        }
        return availability.hasClass("available");
    }

    // <td data-th="EAN">6957303837687</td> -> "6957303837687"
    private String tableValue(Document page, String label) {
        return HtmlUtils.text(page.selectFirst("td[data-th=" + label + "]"));
    }
}
