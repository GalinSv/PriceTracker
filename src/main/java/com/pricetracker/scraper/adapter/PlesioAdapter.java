package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.HtmlUtils;
import com.pricetracker.scraper.PriceParser;
import com.pricetracker.scraper.ScrapedProduct;
import com.pricetracker.scraper.StoreAdapter;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Plesio's sitemap is from 2013, so its products are read from the category pages.
// The product page has no data for machines except a hidden block for Perpetto (ptto-*).
// There is no EAN or model code, only Plesio's own catalog number.
@Component
public class PlesioAdapter implements StoreAdapter {

    private static final String HOME_URL = "https://plesio.bg/";
    // A category page shows 20 products; the next ones come from "?ajax=true&page=N" (the "show more" button)
    private static final int PAGE_SIZE = 20;
    private static final Pattern ALL_COUNT = Pattern.compile("var\\s+allCount\\s*=\\s*(\\d+)");
    private static final Pattern PRODUCT_ID = Pattern.compile("-p-(\\d+)\\.html");
    private static final Pattern CATEGORY_URL = Pattern.compile("-c-\\d+\\.html$");
    private static final Pattern EURO_PRICE = Pattern.compile("([0-9][0-9.,]*)\\s*€");
    private static final Pattern CATALOG_NUMBER = Pattern.compile("(\\d+)");

    @Override
    public String storeCode() {
        return "plesio";
    }

    @Override
    public String categoriesUrl() {
        return HOME_URL;
    }

    // The menu has up to three levels. Only the lowest categories are read: every product is in one of them,
    // and the upper ones would list the same products again.
    @Override
    public List<String> categoryUrls(Document homePage) {
        Set<String> urls = new LinkedHashSet<>();
        for (Element category : homePage.select("#mainCategories li[id^=catal]")) {
            // select() also matches the element itself, so more than one means it has subcategories
            if (category.select("li[id^=catal]").size() > 1) {
                continue;
            }
            for (Element link : category.select("a[href], [data-url]")) {
                String url = link.absUrl(link.hasAttr("data-url") ? "data-url" : "href");
                if (CATEGORY_URL.matcher(url).find()) {
                    urls.add(url);
                    break;
                }
            }
        }
        return new ArrayList<>(urls);
    }

    // The first page says how many products the category has: "var allCount = 226;"
    @Override
    public List<String> morePages(String categoryUrl, Document firstPage) {
        int products = 0;
        for (Element script : firstPage.select("script")) {
            Matcher count = ALL_COUNT.matcher(script.data());
            if (count.find()) {
                products = Integer.parseInt(count.group(1));
                break;
            }
        }
        List<String> pages = new ArrayList<>();
        for (int page = 2; (page - 1) * PAGE_SIZE < products; page++) {
            pages.add(categoryUrl + "?ajax=true&page=" + page);
        }
        return pages;
    }

    @Override
    public Map<String, String> productLinks(Document categoryPage) {
        Map<String, String> links = new LinkedHashMap<>();
        for (Element item : categoryPage.select("div.productListItem")) {
            Element link = item.selectFirst("div.productTitle a[href]");
            if (link == null) {
                continue;
            }
            String url = link.absUrl("href");
            if (PRODUCT_ID.matcher(url).find()) {
                links.putIfAbsent(url, HtmlUtils.text(link));
            }
        }
        return links;
    }

    // A product in two categories has two links: "/category-c-1/name-p-394969.html"
    @Override
    public String productKey(String url) {
        Matcher id = PRODUCT_ID.matcher(url);
        return id.find() ? id.group(1) : url;
    }

    @Override
    public ScrapedProduct parseProduct(Document page) {
        ScrapedProduct product = new ScrapedProduct();
        product.setTitle(HtmlUtils.text(page.selectFirst("h1.productTitle")));
        product.setBrand(HtmlUtils.text(page.selectFirst("div.ptto-brand")));

        // The price as shown; a discount shows the old price in productPriceElementOld.
        // The hidden block's currency still says BGN although the prices are in euro.
        product.setPrice(euroPrice(page.selectFirst("div.productPriceElement")));
        product.setCurrency("EUR");

        // 1 if available, 0 if not
        String available = HtmlUtils.text(page.selectFirst("div.ptto-availability"));
        product.setInStock(available == null ? null : available.equals("1"));

        // "Каталожен номер: 5348978"
        String catalogNumber = HtmlUtils.text(page.selectFirst("div.productSku"));
        if (catalogNumber != null) {
            Matcher number = CATALOG_NUMBER.matcher(catalogNumber);
            if (number.find()) {
                product.setStoreSku(number.group(1));
            }
        }

        Element image = page.selectFirst("img.productMainLargeImage[src]");
        product.setImageUrl(image != null ? image.absUrl("src") : HtmlUtils.text(page.selectFirst("div.ptto-image")));
        return product;
    }

    private BigDecimal euroPrice(Element element) {
        if (element == null) {
            return null;
        }
        Matcher matcher = EURO_PRICE.matcher(element.text());
        return matcher.find() ? PriceParser.parse(matcher.group(1)) : null;
    }
}
