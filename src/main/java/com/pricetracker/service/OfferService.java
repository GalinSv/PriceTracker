package com.pricetracker.service;

import com.pricetracker.dto.PriceSnapshotDto;
import com.pricetracker.dto.ProductDetailsDto;
import com.pricetracker.dto.RefreshResultDto;

import java.util.List;

public interface OfferService {
    ProductDetailsDto importFromUrl(String url);

    // All links become offers of one product, so its page shows every store
    ProductDetailsDto importGroup(List<String> urls);

    // The links become offers of this product. Returns how many offers are new.
    int addOffers(Long productId, List<String> urls);

    List<PriceSnapshotDto> getHistory(Long offerId);

    RefreshResultDto refreshAll();
}
