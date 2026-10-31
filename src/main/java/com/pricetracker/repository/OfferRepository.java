package com.pricetracker.repository;

import com.pricetracker.entity.Offer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OfferRepository extends JpaRepository<Offer, Long> {
    List<Offer> findByProductIdOrderByCurrentPriceAsc(Long productId);

    // All offers of several products in one query (for search results)
    List<Offer> findByProductIdIn(Collection<Long> productIds);
}
