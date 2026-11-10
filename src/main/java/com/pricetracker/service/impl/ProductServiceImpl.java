package com.pricetracker.service.impl;

import com.pricetracker.dto.OfferDto;
import com.pricetracker.dto.ProductDetailsDto;
import com.pricetracker.dto.ProductDto;
import com.pricetracker.entity.Offer;
import com.pricetracker.entity.Product;
import com.pricetracker.exception.ResourceNotFoundException;
import com.pricetracker.repository.OfferRepository;
import com.pricetracker.repository.ProductRepository;
import com.pricetracker.service.ProductService;
import com.pricetracker.util.OfferPrices;
import com.pricetracker.util.TextUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private static final int MAX_SEARCH_RESULTS = 50;

    private final ProductRepository productRepository;
    private final OfferRepository offerRepository;
    private final ModelMapper modelMapper;

    @Override
    public List<ProductDto> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        // Every word must appear somewhere: "logitech g502" finds "Gaming mouse Logitech - G502 Hero"
        Specification<Product> allWords = Arrays.stream(query.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                .map(this::matchesWord)
                .reduce(Specification::and)
                .orElseThrow();
        List<Product> products = productRepository
                .findAll(allWords, PageRequest.of(0, MAX_SEARCH_RESULTS, Sort.by("name")))
                .getContent();
        if (products.isEmpty()) {
            return List.of();
        }

        // Load the offers of all found products with one query, then group them by product
        List<Long> productIds = products.stream().map(Product::getId).toList();
        Map<Long, List<Offer>> offersByProduct = offerRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.groupingBy(offer -> offer.getProduct().getId()));

        return products.stream()
                .map(product -> {
                    List<Offer> offers = offersByProduct.getOrDefault(product.getId(), List.of());
                    ProductDto dto = modelMapper.map(product, ProductDto.class);
                    dto.setLowestPrice(OfferPrices.lowestInStock(offers));
                    dto.setOfferCount(offers.size());
                    return dto;
                })
                .toList();
    }

    @Override
    public ProductDetailsDto getById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        List<Offer> offers = offerRepository.findByProductIdOrderByCurrentPriceAsc(id);

        ProductDetailsDto dto = modelMapper.map(product, ProductDetailsDto.class);
        dto.setOffers(offers.stream()
                .map(offer -> modelMapper.map(offer, OfferDto.class))
                .toList());
        dto.setLowestPrice(OfferPrices.lowestInStock(offers));
        return dto;
    }

    // The word is in the name or the brand, or it is part of the model code ("910-005470")
    private Specification<Product> matchesWord(String word) {
        String pattern = "%" + word + "%";
        String modelCode = TextUtils.normalizeModelCode(word);
        return (root, query, cb) -> {
            Predicate inName = cb.like(cb.lower(root.get("name")), pattern);
            Predicate inBrand = cb.like(cb.lower(root.get("brand")), pattern);
            if (modelCode == null) {
                return cb.or(inName, inBrand);
            }
            return cb.or(inName, inBrand, cb.like(root.get("modelCode"), "%" + modelCode + "%"));
        };
    }
}
