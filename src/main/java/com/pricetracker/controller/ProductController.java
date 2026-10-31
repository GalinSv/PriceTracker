package com.pricetracker.controller;

import com.pricetracker.dto.ApiResponse;
import com.pricetracker.dto.ProductDetailsDto;
import com.pricetracker.dto.ProductDto;
import com.pricetracker.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;


    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductDto>>> search(@RequestParam(defaultValue = "") String query) {
        return ResponseEntity.ok(ApiResponse.ok(productService.search(query)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDetailsDto>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getById(id)));
    }
}
