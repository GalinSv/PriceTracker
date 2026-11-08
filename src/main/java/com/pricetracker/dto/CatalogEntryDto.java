package com.pricetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

// A search result from a store's catalog
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CatalogEntryDto {
    private String storeCode;
    private String storeName;
    private String url;
    private String name;
    private Long productId;
    private BigDecimal price;
    private String currency;
    private Boolean inStock;
    // false = we have no fresh price yet, the page must be opened with /api/catalog/check
    private boolean checked;

    // Known after the page was opened once; results with a shared EAN or the same model key are the same product
    private List<String> eans;
    private String brand;
    // Normalized brand + model code, e.g. "logitech:910006138"
    private String modelKey;
    private String imageUrl;
}
