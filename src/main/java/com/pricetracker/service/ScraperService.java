package com.pricetracker.service;

import com.pricetracker.scraper.ScrapedProduct;

public interface ScraperService {

    ScrapedProduct scrape(String url);
}
