package com.pricetracker.entity;

import com.pricetracker.enums.OfferSource;
import com.pricetracker.enums.OfferStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// A product in a specific store: link + current price.
@Entity
@Table(name = "offers", indexes = {
        @Index(name = "idx_offers_product", columnList = "product_id"),
        @Index(name = "idx_offers_store_sku", columnList = "store_id, store_sku")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // null until we recognize which product the offer belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false, unique = true, length = 1000)
    private String url;

    // The product code used by the store itself
    @Column(name = "store_sku", length = 100)
    private String storeSku;

    // The name as written in the store
    @Column(nullable = false, length = 500)
    private String title;

    // Money: always BigDecimal, never double
    @Column(name = "current_price", precision = 12, scale = 2)
    private BigDecimal currentPrice;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Column(name = "in_stock", nullable = false)
    private Boolean inStock = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OfferSource source = OfferSource.SCRAPER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OfferStatus status = OfferStatus.ACTIVE;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
