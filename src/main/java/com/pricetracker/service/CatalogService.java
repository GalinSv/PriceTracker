package com.pricetracker.service;

import com.pricetracker.dto.CatalogEntryDto;
import com.pricetracker.dto.CatalogPageDto;
import com.pricetracker.dto.CatalogStatusDto;

import java.util.List;

public interface CatalogService {
    // storeCodes: only these stores; null or empty = all. page starts from 0.
    CatalogPageDto search(String query, List<String> storeCodes, int page);

    // Opens the store page of a search result and remembers its name, price and stock
    CatalogEntryDto check(String url);

    CatalogStatusDto getStatus();

    // Starts downloading the store catalogs in the background. Returns false if it is already running.
    boolean startUpdate(boolean force);
}
