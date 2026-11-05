package com.pricetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Catalog search filters; an empty list means "all"
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CatalogFiltersDto {
    // Store codes, e.g. pic, ozone
    private List<String> stores;
    private List<String> brands;
    private List<String> series;
    // "256 GB", "1 TB"
    private List<String> memory;
    // "Черен", "Бял"
    private List<String> colors;
}
