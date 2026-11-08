package com.pricetracker.service.impl;

import com.pricetracker.dto.FavoriteDto;
import com.pricetracker.dto.FavoritesSummaryDto;
import com.pricetracker.entity.Offer;
import com.pricetracker.entity.Product;
import com.pricetracker.entity.User;
import com.pricetracker.entity.WatchlistItem;
import com.pricetracker.exception.ResourceNotFoundException;
import com.pricetracker.repository.OfferRepository;
import com.pricetracker.repository.ProductRepository;
import com.pricetracker.repository.WatchlistItemRepository;
import com.pricetracker.service.FavoriteService;
import com.pricetracker.service.UserService;
import com.pricetracker.util.OfferPrices;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FavoriteServiceImpl implements FavoriteService {

    private static final BigDecimal MAX_TARGET_PRICE = new BigDecimal("1000000");

    private final WatchlistItemRepository watchlistItemRepository;
    private final ProductRepository productRepository;
    private final OfferRepository offerRepository;
    private final UserService userService;

    @Override
    public List<FavoriteDto> getFavorites() {
        List<WatchlistItem> items = userItems();
        Map<Long, List<Offer>> offersByProduct = offersByProduct(items);
        return items.stream()
                .map(item -> toDto(item, offersByProduct.getOrDefault(item.getProduct().getId(), List.of())))
                .sorted(Comparator.comparing(FavoriteDto::isNews).reversed())
                .toList();
    }

    @Override
    public FavoritesSummaryDto getSummary() {
        List<FavoriteDto> favorites = getFavorites();
        return new FavoritesSummaryDto(
                favorites.stream().map(FavoriteDto::getProductId).toList(),
                (int) favorites.stream().filter(FavoriteDto::isNews).count());
    }

    @Override
    public FavoriteDto add(Long productId) {
        User user = userService.currentUser();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        List<Offer> offers = offerRepository.findWithStoreByProductIdIn(List.of(productId));
        WatchlistItem item = watchlistItemRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElseGet(() -> {
                    BigDecimal price = OfferPrices.lowestInStock(offers);
                    WatchlistItem created = new WatchlistItem();
                    created.setUser(user);
                    created.setProduct(product);
                    created.setPriceWhenAdded(price);
                    created.setSeenPrice(price);
                    created.setSeenAt(LocalDateTime.now());
                    return watchlistItemRepository.save(created);
                });
        return toDto(item, offers);
    }

    @Override
    public FavoriteDto setTargetPrice(Long productId, BigDecimal targetPrice) {
        if (targetPrice != null && (targetPrice.signum() <= 0 || targetPrice.compareTo(MAX_TARGET_PRICE) > 0)) {
            throw new IllegalArgumentException("The target price must be between 0 and " + MAX_TARGET_PRICE);
        }
        WatchlistItem item = watchlistItemRepository
                .findByUserIdAndProductId(userService.currentUser().getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product is not in the favorites: " + productId));
        item.setTargetPrice(targetPrice == null ? null : targetPrice.setScale(2, RoundingMode.HALF_UP));
        return toDto(item, offerRepository.findWithStoreByProductIdIn(List.of(productId)));
    }

    @Override
    public void remove(Long productId) {
        watchlistItemRepository.findByUserIdAndProductId(userService.currentUser().getId(), productId)
                .ifPresent(watchlistItemRepository::delete);
    }

    @Override
    public void markSeen() {
        List<WatchlistItem> items = userItems();
        Map<Long, List<Offer>> offersByProduct = offersByProduct(items);
        LocalDateTime now = LocalDateTime.now();
        items.forEach(item -> {
            List<Offer> offers = offersByProduct.getOrDefault(item.getProduct().getId(), List.of());
            item.setSeenPrice(OfferPrices.lowestInStock(offers));
            item.setSeenAt(now);
        });
    }

    private List<WatchlistItem> userItems() {
        return watchlistItemRepository.findByUserIdOrderByCreatedAtDesc(userService.currentUser().getId());
    }

    // One query for the offers of all favorites
    private Map<Long, List<Offer>> offersByProduct(List<WatchlistItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        List<Long> productIds = items.stream().map(item -> item.getProduct().getId()).toList();
        return offerRepository.findWithStoreByProductIdIn(productIds).stream()
                .collect(Collectors.groupingBy(offer -> offer.getProduct().getId()));
    }

    private FavoriteDto toDto(WatchlistItem item, List<Offer> offers) {
        Product product = item.getProduct();
        BigDecimal lowestPrice = OfferPrices.lowestInStock(offers);
        List<String> newStores = offers.stream()
                .filter(offer -> item.getSeenAt() != null && offer.getCreatedAt().isAfter(item.getSeenAt()))
                .map(offer -> offer.getStore().getName())
                .distinct()
                .toList();

        FavoriteDto dto = new FavoriteDto();
        dto.setProductId(product.getId());
        dto.setName(product.getName());
        dto.setBrand(product.getBrand());
        dto.setImageUrl(product.getImageUrl());
        dto.setLowestPrice(lowestPrice);
        dto.setPriceWhenAdded(item.getPriceWhenAdded());
        dto.setSeenPrice(item.getSeenPrice());
        dto.setTargetPrice(item.getTargetPrice());
        dto.setTargetReached(item.getTargetPrice() != null && lowestPrice != null
                && lowestPrice.compareTo(item.getTargetPrice()) <= 0);
        dto.setInStockCount((int) offers.stream().filter(offer -> Boolean.TRUE.equals(offer.getInStock())).count());
        dto.setOfferCount(offers.size());
        dto.setNewStores(newStores);
        dto.setNews(!newStores.isEmpty() || !samePrice(lowestPrice, item.getSeenPrice()));
        dto.setAddedAt(item.getCreatedAt());
        return dto;
    }

    // Also true when both are null (out of stock everywhere both times)
    private boolean samePrice(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }
}
