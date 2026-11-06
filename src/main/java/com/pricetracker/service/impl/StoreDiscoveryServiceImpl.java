package com.pricetracker.service.impl;

import com.pricetracker.dto.CatalogEntryDto;
import com.pricetracker.entity.Product;
import com.pricetracker.exception.ResourceNotFoundException;
import com.pricetracker.repository.OfferRepository;
import com.pricetracker.repository.ProductRepository;
import com.pricetracker.service.CatalogService;
import com.pricetracker.service.OfferService;
import com.pricetracker.service.StoreDiscoveryService;
import com.pricetracker.util.TextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// A tracked product can show up in a new store. We search the catalog by the product's name and add a store
// only when its page shows the same EAN or brand + model - never by name alone, so no wrong product gets in.
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreDiscoveryServiceImpl implements StoreDiscoveryService {

    private static final int MAX_CANDIDATES = 30;
    private static final int MAX_PARALLEL_CHECKS = 10;
    // "Смартфон Apple - iPhone 17 Pro Max, 6.9''" -> "Apple iPhone 17 Pro Max 6 9": the words other stores use too
    private static final Pattern LATIN_WORD = Pattern.compile("[A-Za-z0-9]+");

    private final ProductRepository productRepository;
    private final OfferRepository offerRepository;
    private final CatalogService catalogService;
    private final OfferService offerService;
    private final TransactionTemplate transactionTemplate;

    private final AtomicBoolean running = new AtomicBoolean(false);

    // What we need to know about the product, read inside a transaction
    private record KnownProduct(String query, Set<String> eans, String modelKey, Set<String> storeCodes) {
    }

    @Override
    public int findNewStores(Long productId) {
        KnownProduct known = transactionTemplate.execute(status -> {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
            Set<String> storeCodes = offerRepository.findByProductIdOrderByCurrentPriceAsc(productId).stream()
                    .map(offer -> offer.getStore().getCode())
                    .collect(Collectors.toSet());
            String modelKey = product.getBrand() != null && product.getModelCode() != null
                    ? TextUtils.normalizeBrand(product.getBrand()) + ":" + product.getModelCode()
                    : null;
            return new KnownProduct(searchQuery(product), Set.copyOf(product.getEans()), modelKey, storeCodes);
        });
        if (known == null || (known.eans().isEmpty() && known.modelKey() == null)) {
            return 0;   // nothing to recognize the product by
        }

        List<CatalogEntryDto> candidates = catalogService.search(known.query(), null, 0, MAX_CANDIDATES, false).getEntries()
                .stream()
                .filter(entry -> !known.storeCodes().contains(entry.getStoreCode()))
                .toList();
        List<String> matches = checkAll(candidates).stream()
                .filter(entry -> isSameProduct(entry, known))
                .map(CatalogEntryDto::getUrl)
                .toList();
        if (matches.isEmpty()) {
            return 0;
        }
        int added = offerService.addOffers(productId, matches);
        log.info("Product {}: {} new stores", productId, added);
        return added;
    }

    @Override
    public void findNewStoresForAll() {
        if (!running.compareAndSet(false, true)) {
            log.info("Store discovery is already running");
            return;
        }
        try {
            int added = 0;
            for (Product product : productRepository.findAll()) {
                try {
                    added += findNewStores(product.getId());
                } catch (RuntimeException e) {
                    log.warn("Could not look for new stores of product {}: {}", product.getId(), e.getMessage());
                }
            }
            log.info("Store discovery done: {} new offers", added);
        } finally {
            running.set(false);
        }
    }

    private String searchQuery(Product product) {
        List<String> words = new ArrayList<>();
        Matcher matcher = LATIN_WORD.matcher(product.getName());
        while (matcher.find()) {
            words.add(matcher.group());
        }
        if (product.getBrand() != null && words.stream().noneMatch(word -> word.equalsIgnoreCase(product.getBrand()))) {
            words.add(0, product.getBrand());
        }
        return words.isEmpty() ? product.getName() : String.join(" ", words);
    }

    // Opens the candidates' pages (or takes the price check from the last few hours), stores in parallel
    private List<CatalogEntryDto> checkAll(List<CatalogEntryDto> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(candidates.size(), MAX_PARALLEL_CHECKS));
        try {
            List<CompletableFuture<CatalogEntryDto>> checks = candidates.stream()
                    .map(entry -> CompletableFuture.supplyAsync(() -> checkOrNull(entry.getUrl()), pool))
                    .toList();
            return checks.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList();
        } finally {
            pool.shutdown();
        }
    }

    private CatalogEntryDto checkOrNull(String url) {
        try {
            return catalogService.check(url);
        } catch (RuntimeException e) {
            log.debug("Could not check {}: {}", url, e.getMessage());
            return null;
        }
    }

    private boolean isSameProduct(CatalogEntryDto entry, KnownProduct known) {
        return entry.getEans().stream().anyMatch(known.eans()::contains)
                || (known.modelKey() != null && known.modelKey().equals(entry.getModelKey()));
    }
}
