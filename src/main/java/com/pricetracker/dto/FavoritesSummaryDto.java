package com.pricetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

// For the menu badge and the heart buttons
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavoritesSummaryDto {
    private List<Long> productIds = new ArrayList<>();
    // Favorites that changed since the last visit of the favorites page
    private int newsCount;
}
