package com.pricetracker.repository;

import com.pricetracker.entity.Offer;
import com.pricetracker.enums.OfferStatus;
import com.pricetracker.enums.StoreStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OfferRepository extends JpaRepository<Offer, Long> {
    List<Offer> findByProductIdOrderByCurrentPriceAsc(Long productId);

    List<Offer> findByProductIdIn(Collection<Long> productIds);

    @EntityGraph(attributePaths = "store")
    List<Offer> findWithStoreByProductIdIn(Collection<Long> productIds);

    Optional<Offer> findByUrl(String url);

    // Loads the store together with the offer, because the refresh reads it outside a transaction
    @EntityGraph(attributePaths = "store")
    List<Offer> findByStatusNotAndStore_Status(OfferStatus status, StoreStatus storeStatus);
}
