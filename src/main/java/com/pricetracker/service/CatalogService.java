package com.pricetracker.service;

import com.pricetracker.dto.CatalogEntryDto;
import com.pricetracker.dto.CatalogStatusDto;

import java.util.List;

public interface CatalogService {
    List<CatalogEntryDto> search(String query);

    CatalogStatusDto getStatus();

    // Starts downloading the store catalogs in the background. Returns false if it is already running.
    boolean startUpdate(boolean force);
}
