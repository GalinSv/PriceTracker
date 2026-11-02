package com.pricetracker.repository;

import com.pricetracker.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

// JpaSpecificationExecutor lets the service build the search query from any number of words
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Optional<Product> findByEan(String ean);

    List<Product> findByModelCode(String modelCode);
}
