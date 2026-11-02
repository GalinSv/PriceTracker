package com.pricetracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// A product link from a store's sitemap. We know only the link, not the price,
// until someone opens it (then it becomes an Offer).
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
}
