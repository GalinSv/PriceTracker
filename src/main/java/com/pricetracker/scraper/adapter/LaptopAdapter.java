package com.pricetracker.scraper.adapter;

import org.springframework.stereotype.Component;

@Component
public class LaptopAdapter extends DesktopLaptopAdapter {

    @Override
    public String storeCode() {
        return "laptop";
    }

    @Override
    public String sitemapUrl() {
        return "https://laptop.bg/sitemap-laptop.xml.gz";
    }
}
