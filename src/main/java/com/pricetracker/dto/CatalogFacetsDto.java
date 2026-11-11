package com.pricetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// The values of each filter among all links of a search, with how many links have them
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CatalogFacetsDto {
    private List<FacetValue> brands;
    private List<FacetValue> series;
    private List<FacetValue> memory;
    private List<FacetValue> colors;
    // All the links of the search with the chosen filters, also those after the first page
    private long total;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FacetValue {
        private String value;
        private long count;
    }
}
