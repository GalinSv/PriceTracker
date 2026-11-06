package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.MicrodataStoreAdapter;
import com.pricetracker.scraper.ScrapedProduct;
import org.jsoup.nodes.Document;

import java.util.regex.Pattern;

// Desktop.bg and Laptop.bg run on the same shop system with schema.org microdata.
// Their "sku" is the maker's model code, e.g. "CNE-CMSW07B".
abstract class DesktopLaptopAdapter extends MicrodataStoreAdapter {

    // https://laptop.bg/mice-canyon-CNECMSW07B is category-brand-model; "mice-canyon" is a category page
    // and /archive/... are products that are no longer sold
    private static final Pattern PRODUCT_URL = Pattern.compile("https://[^/]+/[^/-]+-[^/-]+-[^/]+");

    @Override
    public boolean isProductUrl(String url) {
        return PRODUCT_URL.matcher(url).matches();
    }

    @Override
    protected void customize(Document page, ScrapedProduct product) {
        if (product.getModelCode() == null) {
            product.setModelCode(product.getStoreSku());
        }
    }
}
