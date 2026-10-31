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
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final OfferRepository offerRepository;
    private final ModelMapper modelMapper;

    @Override
    public List<ProductDto> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<Product> products = productRepository.findByNameContainingIgnoreCaseOrderByNameAsc(query.trim());
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
                    dto.setLowestPrice(lowestInStockPrice(offers));
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
        dto.setLowestPrice(lowestInStockPrice(offers));
        return dto;
    }

    private BigDecimal lowestInStockPrice(List<Offer> offers) {
        return offers.stream()
                .filter(offer -> Boolean.TRUE.equals(offer.getInStock()))
                .map(Offer::getCurrentPrice)
                .filter(price -> price != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }
}
