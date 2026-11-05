package com.pricetracker.repository;

import com.pricetracker.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

// JpaSpecificationExecutor lets the service build the search query from any number of words
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Optional<Product> findByEan(String ean);

    // Products that have any of these barcodes
    @Query("select distinct p from Product p join p.eans ean where ean in :eans order by p.id")
    List<Product> findByAnyEan(@Param("eans") Collection<String> eans);

    List<Product> findByModelCode(String modelCode);
}
