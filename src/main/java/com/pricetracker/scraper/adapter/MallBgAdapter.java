package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.HtmlUtils;
import com.pricetracker.scraper.MicrodataStoreAdapter;
import com.pricetracker.scraper.ScrapedProduct;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

// MallBG has microdata for name, brand and price; the stock is only in <meta property="product:availability">
@Component
public class MallBgAdapter extends MicrodataStoreAdapter {

    @Override
    public String storeCode() {
        return "mallbg";
    }

    @Override
    public String sitemapUrl() {
        return "https://mallbg.com/sitemap.xml";
    }

    // The index also lists categories, news and blog sitemaps
    @Override
    public boolean isProductSitemap(String sitemapUrl) {
        return sitemapUrl.contains("/sitemap/products/");
    }

    @Override
    protected void customize(Document page, ScrapedProduct product) {
        String availability = HtmlUtils.meta(page, "product:availability");
        if (product.getInStock() == null && availability != null) {
            product.setInStock(availability.equalsIgnoreCase("in stock"));
        }
        if (product.getBrand() == null) {
            product.setBrand(HtmlUtils.meta(page, "product:brand"));
        }
    }
}
