package com.pricetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// One store's price for a product
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfferDto {
    private Long id;
    private String storeCode;   // ModelMapper fills it from offer.store.code
    private String storeName;   // ModelMapper fills it from offer.store.name
    private String url;
    private String title;
    private BigDecimal currentPrice;
    private String currency;
    private Boolean inStock;
    private LocalDateTime lastCheckedAt;
}
