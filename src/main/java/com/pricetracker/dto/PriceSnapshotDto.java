package com.pricetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceSnapshotDto {
    private BigDecimal price;
    private String currency;
    private Boolean inStock;
    private LocalDateTime capturedAt;
}
