package com.pricetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class CatalogPageDto {
    private List<CatalogEntryDto> entries;
    private boolean hasMore;
    // Only with the first page; null on the next ones
    private CatalogFacetsDto facets;
}
