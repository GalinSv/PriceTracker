package com.pricetracker.repository;

import com.pricetracker.entity.CatalogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogEntryRepository extends JpaRepository<CatalogEntry, Long> {
    long countByStoreId(Long storeId);
}
