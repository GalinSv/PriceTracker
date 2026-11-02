package com.pricetracker.repository;

import com.pricetracker.entity.PriceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {
    List<PriceSnapshot> findByOfferIdOrderByCapturedAtAsc(Long offerId);
}
