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
    // In stock, but the store brings it for each order
    private boolean onOrder;
    // false = we have no fresh price yet, the page must be opened with /api/catalog/check
    private boolean checked;

    // Known after the page was opened once; results with a shared EAN or the same model key are the same product
    private List<String> eans;
    private String brand;
    // Normalized brand + model code, e.g. "logitech:910006138"
    private String modelKey;
    private String imageUrl;
    // From the link's name, as in the search filters ("Samsung Galaxy S26 Ultra", "Черен", "256 GB");
    // null when the name doesn't say
    private String series;
    private String color;
    private String memory;
    // The link an admin put this one with, see CatalogService.join; null for most links
    private String joinedToUrl;
    // The store says the page doesn't exist any more; the search leaves such links out
    private boolean gone;
    // Only in a search: a case, cable etc. for the product that was searched, not the product itself
    private boolean accessory;
}
