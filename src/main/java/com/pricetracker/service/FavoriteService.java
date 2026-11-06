package com.pricetracker.service;

import com.pricetracker.dto.FavoriteDto;
import com.pricetracker.dto.FavoritesSummaryDto;

import java.math.BigDecimal;
import java.util.List;

// The favorites of the logged in user
public interface FavoriteService {
    // Changed products first, then the newest
    List<FavoriteDto> getFavorites();

    FavoritesSummaryDto getSummary();

    // Adding a product that is already a favorite changes nothing
    FavoriteDto add(Long productId);

    FavoriteDto setTargetPrice(Long productId, BigDecimal targetPrice);

    void remove(Long productId);

    // Called after the favorites page was shown: its changes are no longer news
    void markSeen();
}
