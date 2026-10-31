package com.pricetracker.service;

import com.pricetracker.dto.ProductDetailsDto;
import com.pricetracker.dto.ProductDto;

import java.util.List;

public interface ProductService {
    List<ProductDto> search(String query);

    ProductDetailsDto getById(Long id);
}
