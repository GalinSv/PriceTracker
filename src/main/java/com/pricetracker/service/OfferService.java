package com.pricetracker.service;

import com.pricetracker.dto.ProductDetailsDto;

public interface OfferService {
    // Reads a product page and saves/updates its offer, product and price history
    ProductDetailsDto importFromUrl(String url);
}
