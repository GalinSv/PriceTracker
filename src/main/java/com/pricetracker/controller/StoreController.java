package com.pricetracker.controller;

import com.pricetracker.dto.ApiResponse;
import com.pricetracker.dto.StoreDto;
import com.pricetracker.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreDto>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(storeService.getAll()));
    }

    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<StoreDto>> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(storeService.getByCode(code)));
    }
}
