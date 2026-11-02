package com.pricetracker.service.impl;

import com.pricetracker.dto.ProductDetailsDto;
import com.pricetracker.entity.Offer;
import com.pricetracker.entity.PriceSnapshot;
import com.pricetracker.entity.Product;
import com.pricetracker.entity.Store;
import com.pricetracker.enums.OfferSource;
import com.pricetracker.enums.OfferStatus;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

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

    @Override
    public ProductDetailsDto importFromUrl(String url) {

        ScrapedProduct scraped = scraperService.scrape(url);

        Long productId = transactionTemplate.execute(status -> save(scraped));
        return productService.getById(productId);
    }

    private Long save(ScrapedProduct scraped) {
        Store store = storeRepository.findByCode(scraped.getStoreCode()).orElseThrow();
        Offer offer = offerRepository.findByUrl(scraped.getUrl())
                .orElseGet(() -> newOffer(store, scraped.getUrl()));

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
        offer.setStatus(OfferStatus.ACTIVE);
        offer.setLastCheckedAt(LocalDateTime.now());
        offerRepository.save(offer);

        if (changed) {
            priceSnapshotRepository.save(snapshotOf(offer));
        }
        store.setLastSuccessAt(LocalDateTime.now());
        return offer.getProduct().getId();
    }

    private Offer newOffer(Store store, String url) {
        Offer offer = new Offer();
        offer.setStore(store);
        offer.setUrl(url);
        offer.setSource(OfferSource.SCRAPER);
        return offer;
    }

    // The same product in different stores becomes one Product:
    // 1) same EAN (barcode) - the safest match
    // 2) same brand + model code, e.g. Logitech + 910-005470
    // 3) otherwise it is a new product
    private Product findOrCreateProduct(ScrapedProduct scraped) {
        if (scraped.getEan() != null) {
            Optional<Product> byEan = productRepository.findByEan(scraped.getEan());
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
        product.setEan(scraped.getEan());
        product.setImageUrl(TextUtils.truncate(scraped.getImageUrl(), 1000));
        return productRepository.save(product);
    }

    // A store that knows more (e.g. has the EAN) completes the product
    private void fillMissingProductData(Product product, ScrapedProduct scraped) {
        if (product.getEan() == null && scraped.getEan() != null
                && productRepository.findByEan(scraped.getEan()).isEmpty()) {
            product.setEan(scraped.getEan());
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
}
