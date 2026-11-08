package com.pricetracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// A product link from a store's sitemap. The sitemap gives only the link;
// the name, price and stock are filled in when the link shows up in a search.
@Entity
@Table(name = "catalog_entries", indexes = {
        @Index(name = "idx_catalog_entries_store", columnList = "store_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CatalogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false, unique = true, length = 1000)
    private String url;

    @Column(name = "search_text", nullable = false, length = 1000)
    private String searchText;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    // Read from the product page when the link shows up in a search
    @Column(length = 500)
    private String title;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(length = 3)
    private String currency;

    @Column(name = "in_stock")
    private Boolean inStock;

    // For grouping the same product from different stores in a search.
    // Comma-separated when the store lists several barcodes.
    @Column(length = 200)
    private String ean;

    @Column(length = 100)
    private String brand;

    // Normalized like Product.modelCode: "910-006138" -> "910006138"
    @Column(name = "model_code", length = 100)
    private String modelCode;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    // Search filters guessed from the name when the catalog is updated, see FacetExtractor
    @Column(name = "facet_brand", length = 100)
    private String facetBrand;

    @Column(name = "facet_series", length = 150)
    private String facetSeries;

    @Column(name = "facet_memory", length = 10)
    private String facetMemory;

    @Column(name = "facet_color", length = 20)
    private String facetColor;

    // When the page was last opened; null = never
    @Column(name = "checked_at")
    private LocalDateTime checkedAt;
}
