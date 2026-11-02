package com.pricetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// A search result from a store's catalog: a link without a price yet
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CatalogEntryDto {
    private String storeCode;
    private String storeName;
    private String url;
    private String name;
    private Long productId;
}
