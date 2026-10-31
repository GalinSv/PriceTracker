package com.pricetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Short product info for search results
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {
    private Long id;
    private String name;
    private String brand;
    private String imageUrl;
    private BigDecimal lowestPrice;
    private int offerCount;
}
