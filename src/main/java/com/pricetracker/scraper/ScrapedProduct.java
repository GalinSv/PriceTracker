package com.pricetracker.scraper;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// What we read from one product page in one store
@Data
@NoArgsConstructor
public class ScrapedProduct {
    private String url;
    private String storeCode;
    private String title;
    private String brand;
    private String modelCode;
    private String ean;
    private String storeSku;
    private BigDecimal price;
    private String currency;
    private Boolean inStock;
    private String imageUrl;
}
