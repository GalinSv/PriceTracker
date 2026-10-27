package com.pricetracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// The canonical product, e.g. Sony WH-1000XM5. Per-store prices live in Offer.
@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_model_code", columnList = "model_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(length = 100)
    private String brand;

    // Normalized model code, e.g. WH1000XM5. Used to match offers from different stores.
    @Column(name = "model_code", length = 100)
    private String modelCode;

    // Barcode. Not every store shows it, so it can be null.
    @Column(unique = true, length = 20)
    private String ean;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
