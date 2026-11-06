package com.pricetracker.service;

public interface StoreDiscoveryService {

    // Adds the stores that sell a tracked product but are not among its offers yet. Returns how many were added.
    int findNewStores(Long productId);

    // The daily run over every tracked product
    void findNewStoresForAll();
}
