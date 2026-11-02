package com.pricetracker.controller;

import com.pricetracker.dto.ApiResponse;
import com.pricetracker.scraper.ScrapedProduct;
import com.pricetracker.service.ScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scraper")
@RequiredArgsConstructor
public class ScraperController {

    private final ScraperService scraperService;

    // Shows what we read from a product page, without saving it
    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<ScrapedProduct>> preview(@RequestParam String url) {
        return ResponseEntity.ok(ApiResponse.ok(scraperService.scrape(url)));
    }
}
