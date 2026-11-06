package com.pricetracker.scraper;

import com.pricetracker.exception.ScrapingException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.List;

// For stores that mark up the page itself with schema.org microdata (itemprop="price" etc.)
// instead of adding a JSON-LD block
public abstract class MicrodataStoreAdapter implements StoreAdapter {

    private static final List<String> GTINS = List.of("gtin13", "gtin", "gtin12", "gtin14", "gtin8");

    @Override
    public ScrapedProduct parseProduct(Document page) {
        Element item = page.selectFirst("[itemscope][itemtype~=(?i)schema\\.org/product$]");
        if (item == null) {
            throw new ScrapingException("No product data (microdata) found on the page");
        }
        // The price can be in its own Offer item or directly in the product
        Element offer = property(item, "offers");
        Element offerItem = offer != null && offer.hasAttr("itemscope") ? offer : item;

        ScrapedProduct product = new ScrapedProduct();
        String name = value(property(item, "name"));
        product.setTitle(name != null ? name : HtmlUtils.text(page.selectFirst("h1")));
        product.setBrand(brand(property(item, "brand")));
        product.setModelCode(value(property(item, "mpn")));
        product.setStoreSku(value(property(item, "sku")));
        GTINS.forEach(gtin -> product.addEans(value(property(item, gtin))));
        product.setPrice(PriceParser.parse(value(property(offerItem, "price"))));
        product.setCurrency(value(property(offerItem, "priceCurrency")));
        product.setInStock(Availability.isInStock(value(property(offerItem, "availability"))));
        String ogImage = HtmlUtils.meta(page, "og:image");
        product.setImageUrl(ogImage != null ? ogImage : value(property(item, "image")));

        customize(page, product);
        return product;
    }

    protected void customize(Document page, ScrapedProduct product) {
    }

    // The first property of this item itself, not of an item inside it (breadcrumbs, related products...)
    private Element property(Element item, String name) {
        for (Element element : item.select("[itemprop=" + name + "]")) {
            if (owner(element) == item) {
                return element;
            }
        }
        return null;
    }

    private Element owner(Element element) {
        Element parent = element.parent();
        while (parent != null && !parent.hasAttr("itemscope")) {
            parent = parent.parent();
        }
        return parent;
    }

    // <meta content="...">, <link href="...">, <img src="..."> or the text
    private String value(Element element) {
        if (element == null) {
            return null;
        }
        if (element.hasAttr("content")) {
            return HtmlUtils.blankToNull(element.attr("content"));
        }
        if (element.is("link, a")) {
            return HtmlUtils.blankToNull(element.absUrl("href"));
        }
        if (element.is("img")) {
            return HtmlUtils.blankToNull(element.absUrl("src"));
        }
        return HtmlUtils.text(element);
    }

    // The brand is either a text or an item with its own name
    private String brand(Element element) {
        if (element != null && element.hasAttr("itemscope")) {
            return value(property(element, "name"));
        }
        return value(element);
    }
}
