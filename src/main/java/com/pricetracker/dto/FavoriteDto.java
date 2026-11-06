package com.pricetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// A product in the user's favorites, with what changed since it was added and since the last visit
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteDto {
    private Long productId;
    private String name;
    private String brand;
    private String imageUrl;
    // null when no store has it in stock
    private BigDecimal lowestPrice;
    private BigDecimal priceWhenAdded;
    // The lowest price at the last visit of the favorites page
    private BigDecimal seenPrice;
    private BigDecimal targetPrice;
    private boolean targetReached;
    private int inStockCount;
    private int offerCount;
    // Stores added after the last visit
    private List<String> newStores = new ArrayList<>();
    private boolean news;
    private LocalDateTime addedAt;
}
