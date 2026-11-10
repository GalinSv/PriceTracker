package com.pricetracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// A product in a user's favorites. A user can add the same product only once.
@Entity
@Table(name = "watchlist_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_watchlist_user_product", columnNames = {"user_id", "product_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Optional: the product is marked when its lowest price drops to this or below
    @Column(name = "target_price", precision = 12, scale = 2)
    private BigDecimal targetPrice;

    // The lowest price on the day the product was added
    @Column(name = "price_when_added", precision = 12, scale = 2)
    private BigDecimal priceWhenAdded;

    // The lowest price and the time the user last opened the favorites page; changes after it are news
    @Column(name = "seen_price", precision = 12, scale = 2)
    private BigDecimal seenPrice;

    @Column(name = "seen_at")
    private LocalDateTime seenAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
