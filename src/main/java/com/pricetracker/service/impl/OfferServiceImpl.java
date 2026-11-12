package com.pricetracker.service.impl;

import com.pricetracker.dto.PriceSnapshotDto;
import com.pricetracker.dto.ProductDetailsDto;
import com.pricetracker.dto.RefreshResultDto;
import com.pricetracker.entity.Offer;
import com.pricetracker.entity.PriceSnapshot;
import com.pricetracker.entity.Product;
import com.pricetracker.entity.Store;
import com.pricetracker.enums.OfferSource;
import com.pricetracker.enums.OfferStatus;
import com.pricetracker.enums.StoreStatus;
import com.pricetracker.exception.NoPriceException;
import com.pricetracker.exception.PageNotFoundException;
import com.pricetracker.exception.ResourceNotFoundException;
import com.pricetracker.exception.ScrapingException;
import com.pricetracker.repository.OfferRepository;
import com.pricetracker.repository.PriceSnapshotRepository;
import com.pricetracker.repository.ProductRepository;
import com.pricetracker.repository.StoreRepository;
import com.pricetracker.scraper.ScrapedProduct;
import com.pricetracker.service.OfferService;
import com.pricetracker.service.ProductService;
import com.pricetracker.service.ScraperService;
import com.pricetracker.util.TextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OfferServiceImpl implements OfferService {

    private final ScraperService scraperService;
    private final ProductService productService;
    private final StoreRepository storeRepository;
    private final OfferRepository offerRepository;
    private final ProductRepository productRepository;
    private final PriceSnapshotRepository priceSnapshotRepository;
    private final TransactionTemplate transactionTemplate;
    private final ModelMapper modelMapper;

    private static final int MAX_GROUP_SIZE = 30;
    private static final int MAX_PARALLEL_PAGES = 10;

    private final AtomicBoolean refreshRunning = new AtomicBoolean(false);

    @Override
    public ProductDetailsDto importFromUrl(String url) {

        ScrapedProduct scraped = scraperService.scrape(url);

        Long productId = transactionTemplate.execute(status -> {
            Offer offer = offerRepository.findByUrl(scraped.getUrl()).orElseGet(() -> newOffer(scraped));
            applyScraped(offer, scraped);
            return offer.getProduct().getId();
        });
        return productService.getById(productId);
    }

    @Override
    public ProductDetailsDto importGroup(List<String> urls) {
        List<ScrapedProduct> found = scrapePages(urls);
        Long productId = transactionTemplate.execute(status -> {
            List<Offer> offers = offersFor(found);
            // A store that is already tracked decides the product; otherwise the usual matching does
            Product product = offers.stream().map(Offer::getProduct).filter(Objects::nonNull).findFirst()
                    .orElseGet(() -> findOrCreateProduct(richest(found)));
            attach(product, offers, found);
            return product.getId();
        });
        return productService.getById(productId);
    }

    @Override
    public int addOffers(Long productId, List<String> urls) {
        List<ScrapedProduct> found = scrapePages(urls);
        Integer added = transactionTemplate.execute(status -> {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
            List<Offer> offers = offersFor(found);
            int newOffers = (int) offers.stream().filter(offer -> offer.getProduct() == null).count();
            attach(product, offers, found);
            return newOffers;
        });
        return added == null ? 0 : added;
    }

    private List<ScrapedProduct> scrapePages(List<String> urls) {
        if (urls == null || urls.isEmpty() || urls.size() > MAX_GROUP_SIZE) {
            throw new IllegalArgumentException("Give between 1 and " + MAX_GROUP_SIZE + " links");
        }
        // Two links can lead to the same page, so each page is kept once
        Map<String, ScrapedProduct> pages = new LinkedHashMap<>();
        scrapeAll(urls).forEach(page -> pages.putIfAbsent(page.getUrl(), page));
        if (pages.isEmpty()) {
            throw new ScrapingException("None of the store pages could be read");
        }
        return List.copyOf(pages.values());
    }

    private List<Offer> offersFor(List<ScrapedProduct> pages) {
        return pages.stream()
                .map(page -> offerRepository.findByUrl(page.getUrl()).orElseGet(() -> newOffer(page)))
                .toList();
    }

    // Offers without a product join this one; offers of another product stay where they are
    private void attach(Product product, List<Offer> offers, List<ScrapedProduct> pages) {
        for (int i = 0; i < pages.size(); i++) {
            Offer offer = offers.get(i);
            if (offer.getProduct() == null) {
                offer.setProduct(product);
            }
            applyScraped(offer, pages.get(i));
        }
    }

    // Pages of different stores are read at the same time; the rate limiter still spaces out one store
    private List<ScrapedProduct> scrapeAll(List<String> urls) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(urls.size(), MAX_PARALLEL_PAGES));
        try {
            List<CompletableFuture<ScrapedProduct>> pages = urls.stream()
                    .map(url -> CompletableFuture.supplyAsync(() -> scrapeOrNull(url), pool))
                    .toList();
            return pages.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList();
        } finally {
            pool.shutdown();
        }
    }

    // A removed product or a store that is down should not stop the other stores
    private ScrapedProduct scrapeOrNull(String url) {
        try {
            return scraperService.scrape(url);
        } catch (RuntimeException e) {
            log.info("Skipped {}: {}", url, e.getMessage());
            return null;
        }
    }

    // The page with an EAN and a model code is the best one to match the other stores against
    private ScrapedProduct richest(List<ScrapedProduct> pages) {
        return pages.stream()
                .max(Comparator.comparingInt(page -> (page.getEans().isEmpty() ? 0 : 2) + (page.getModelCode() == null ? 0 : 1)))
                .orElseThrow();
    }

    @Override
    public List<PriceSnapshotDto> getHistory(Long offerId) {
        if (!offerRepository.existsById(offerId)) {
            throw new ResourceNotFoundException("Offer not found: " + offerId);
        }
        return priceSnapshotRepository.findByOfferIdOrderByCapturedAtAsc(offerId).stream()
                .map(snapshot -> modelMapper.map(snapshot, PriceSnapshotDto.class))
                .toList();
    }

    @Override
    public RefreshResultDto refreshAll() {
        if (!refreshRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("A price refresh is already running");
        }
        try {
            List<Offer> offers = offerRepository.findByStatusNotAndStore_Status(OfferStatus.NOT_FOUND, StoreStatus.ACTIVE);
            Map<String, List<Offer>> offersByStore = offers.stream()
                    .collect(Collectors.groupingBy(offer -> offer.getStore().getCode()));
            if (offersByStore.isEmpty()) {
                return new RefreshResultDto();
            }

            // One thread per store: stores are checked in parallel, but each store gets one request at a time
            RefreshCounters counters = new RefreshCounters();
            ExecutorService pool = Executors.newFixedThreadPool(offersByStore.size());
            try {
                CompletableFuture.allOf(offersByStore.values().stream()
                        .map(storeOffers -> CompletableFuture.runAsync(
                                () -> storeOffers.forEach(offer -> refreshOne(offer, counters)), pool))
                        .toArray(CompletableFuture[]::new)).join();
            } finally {
                pool.shutdown();
            }
            RefreshResultDto result = counters.toDto();
            log.info("Price refresh done: {}", result);
            return result;
        } finally {
            refreshRunning.set(false);
        }
    }

    private void refreshOne(Offer offer, RefreshCounters counters) {
        counters.checked.incrementAndGet();
        try {
            ScrapedProduct scraped = scraperService.scrape(offer.getUrl());
            Boolean changed = transactionTemplate.execute(status ->
                    applyScraped(offerRepository.findById(offer.getId()).orElseThrow(), scraped));
            if (Boolean.TRUE.equals(changed)) {
                counters.changed.incrementAndGet();
            }
        } catch (PageNotFoundException e) {
            counters.notFound.incrementAndGet();
            markFailed(offer.getId(), OfferStatus.NOT_FOUND, null);
        } catch (NoPriceException e) {
            Boolean changed = transactionTemplate.execute(status ->
                    markSoldOut(offerRepository.findById(offer.getId()).orElseThrow()));
            if (Boolean.TRUE.equals(changed)) {
                counters.changed.incrementAndGet();
            }
        } catch (RuntimeException e) {
            counters.failed.incrementAndGet();
            log.warn("Could not refresh offer {}: {}", offer.getId(), e.getMessage());
            markFailed(offer.getId(), OfferStatus.ERROR, e.getMessage());
        }
    }

    private void markFailed(Long offerId, OfferStatus status, String error) {
        transactionTemplate.executeWithoutResult(tx -> offerRepository.findById(offerId).ifPresent(offer -> {
            offer.setStatus(status);
            offer.setLastCheckedAt(LocalDateTime.now());
            if (error != null) {
                offer.getStore().setLastError(TextUtils.truncate(error, 1000));
            }
        }));
    }

    // The store zeroed the price: the offer keeps its last real price and is no longer in stock.
    // Returns true if it was in stock until now (and a history row was saved).
    private boolean markSoldOut(Offer offer) {
        boolean changed = !Boolean.FALSE.equals(offer.getInStock());
        offer.setInStock(false);
        offer.setOnOrder(false);
        offer.setStatus(OfferStatus.ACTIVE);
        offer.setLastCheckedAt(LocalDateTime.now());
        offerRepository.save(offer);
        if (changed) {
            priceSnapshotRepository.save(snapshotOf(offer));
        }
        offer.getStore().setLastSuccessAt(LocalDateTime.now());
        return changed;
    }

    // Returns true if the price or the stock changed (and a history row was saved)
    private boolean applyScraped(Offer offer, ScrapedProduct scraped) {
        if (offer.getProduct() == null) {
            offer.setProduct(findOrCreateProduct(scraped));
        }
        fillMissingProductData(offer.getProduct(), scraped);

        boolean changed = offer.getId() == null
                || !samePrice(offer.getCurrentPrice(), scraped.getPrice())
                || !Objects.equals(offer.getInStock(), scraped.getInStock());

        offer.setTitle(TextUtils.truncate(scraped.getTitle(), 500));
        offer.setStoreSku(TextUtils.truncate(scraped.getStoreSku(), 100));
        offer.setCurrentPrice(scraped.getPrice());
        offer.setCurrency(scraped.getCurrency());
        offer.setInStock(scraped.getInStock());
        offer.setOnOrder(scraped.isOnOrder());
        offer.setStatus(OfferStatus.ACTIVE);
        offer.setLastCheckedAt(LocalDateTime.now());
        offerRepository.save(offer);

        if (changed) {
            priceSnapshotRepository.save(snapshotOf(offer));
        }
        offer.getStore().setLastSuccessAt(LocalDateTime.now());
        return changed;
    }

    private Offer newOffer(ScrapedProduct scraped) {
        Store store = storeRepository.findByCode(scraped.getStoreCode()).orElseThrow();
        Offer offer = new Offer();
        offer.setStore(store);
        offer.setUrl(scraped.getUrl());
        offer.setSource(OfferSource.SCRAPER);
        return offer;
    }

    // The same product in different stores becomes one Product:
    // 1) a shared EAN (barcode) - the safest match
    // 2) same brand + model code, e.g. Logitech + 910-005470
    // 3) otherwise it is a new product
    private Product findOrCreateProduct(ScrapedProduct scraped) {
        if (!scraped.getEans().isEmpty()) {
            Optional<Product> byEan = productRepository.findByAnyEan(scraped.getEans()).stream().findFirst();
            if (byEan.isPresent()) {
                return byEan.get();
            }
        }

        String modelCode = TextUtils.normalizeModelCode(scraped.getModelCode());
        if (modelCode != null && scraped.getBrand() != null) {
            String brand = TextUtils.normalizeBrand(scraped.getBrand());
            Optional<Product> byModel = productRepository.findByModelCode(modelCode).stream()
                    .filter(product -> brand.equals(TextUtils.normalizeBrand(product.getBrand())))
                    .findFirst();
            if (byModel.isPresent()) {
                return byModel.get();
            }
        }

        Product product = new Product();
        product.setName(TextUtils.truncate(scraped.getTitle(), 500));
        product.setBrand(TextUtils.truncate(scraped.getBrand(), 100));
        product.setModelCode(modelCode);
        product.setImageUrl(TextUtils.truncate(scraped.getImageUrl(), 1000));
        return productRepository.save(product);
    }

    // A store that knows more (e.g. has the EAN) completes the product
    private void fillMissingProductData(Product product, ScrapedProduct scraped) {
        // Every barcode a store shows helps to recognize the product in the next store
        product.getEans().addAll(scraped.getEans());
        if (product.getEan() == null && !scraped.getEans().isEmpty()
                && productRepository.findByEan(scraped.getEans().get(0)).isEmpty()) {
            product.setEan(scraped.getEans().get(0));
        }
        if (product.getModelCode() == null) {
            product.setModelCode(TextUtils.normalizeModelCode(scraped.getModelCode()));
        }
        if (product.getBrand() == null) {
            product.setBrand(TextUtils.truncate(scraped.getBrand(), 100));
        }
        if (product.getImageUrl() == null) {
            product.setImageUrl(TextUtils.truncate(scraped.getImageUrl(), 1000));
        }
    }

    private PriceSnapshot snapshotOf(Offer offer) {
        PriceSnapshot snapshot = new PriceSnapshot();
        snapshot.setOffer(offer);
        snapshot.setPrice(offer.getCurrentPrice());
        snapshot.setCurrency(offer.getCurrency());
        snapshot.setInStock(offer.getInStock());
        return snapshot;
    }


    private boolean samePrice(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static class RefreshCounters {
        final AtomicInteger checked = new AtomicInteger();
        final AtomicInteger changed = new AtomicInteger();
        final AtomicInteger notFound = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();

        RefreshResultDto toDto() {
            return new RefreshResultDto(checked.get(), changed.get(), notFound.get(), failed.get());
        }
    }
}
