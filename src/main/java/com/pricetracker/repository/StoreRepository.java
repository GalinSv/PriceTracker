package com.pricetracker.repository;

import com.pricetracker.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {
    Optional<Store> findByCode(String code);
}