package com.pricetracker.scraper.adapter;

import org.springframework.stereotype.Component;

@Component
public class DesktopAdapter extends DesktopLaptopAdapter {

    @Override
    public String storeCode() {
        return "desktop";
    }

    @Override
    public String sitemapUrl() {
        return "https://desktop.bg/sitemap-desktop.xml.gz";
    }
}
