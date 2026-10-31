package com.pricetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// Product page: the product with its prices in every store
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailsDto {
    private Long id;
    private String name;
    private String brand;
    private String modelCode;
    private String ean;
    private String imageUrl;
    private String categoryName;   // ModelMapper fills it from product.category.name
    private BigDecimal lowestPrice;
    private List<OfferDto> offers = new ArrayList<>();
}
