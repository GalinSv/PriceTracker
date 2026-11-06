package com.pricetracker.repository;

import com.pricetracker.entity.WatchlistItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {
    @EntityGraph(attributePaths = "product")
    List<WatchlistItem> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<WatchlistItem> findByUserIdAndProductId(Long userId, Long productId);
}
