package com.pricetracker.service;

import com.pricetracker.dto.PriceSnapshotDto;
import com.pricetracker.dto.ProductDetailsDto;
import com.pricetracker.dto.RefreshResultDto;

import java.util.List;

public interface OfferService {
    ProductDetailsDto importFromUrl(String url);

    List<PriceSnapshotDto> getHistory(Long offerId);

    RefreshResultDto refreshAll();
}
