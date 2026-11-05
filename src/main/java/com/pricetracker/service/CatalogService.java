package com.pricetracker.service;

import com.pricetracker.dto.CatalogEntryDto;
import com.pricetracker.dto.CatalogFiltersDto;
import com.pricetracker.dto.CatalogPageDto;
import com.pricetracker.dto.CatalogStatusDto;

public interface CatalogService {
    // filters may be null. page starts from 0; size is capped at 300.
    // withFacets: also count the brands, series, memory and colors of all results (one more query).
    CatalogPageDto search(String query, CatalogFiltersDto filters, int page, int size, boolean withFacets);

    // Opens the store page of a search result and remembers its name, price and stock
    CatalogEntryDto check(String url);

    CatalogStatusDto getStatus();

    // Starts downloading the store catalogs in the background. Returns false if it is already running.
    boolean startUpdate(boolean force);

    // Reads brand, series, memory and color of every link again, e.g. after brands.txt changed.
    // Runs in the background; returns false if a catalog job is already running.
    boolean startFacetRefresh();
}
