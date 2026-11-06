package com.pricetracker.controller;

import com.pricetracker.dto.ApiResponse;
import com.pricetracker.dto.FavoriteDto;
import com.pricetracker.dto.FavoritesSummaryDto;
import com.pricetracker.dto.TargetPriceRequest;
import com.pricetracker.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Only for logged in users (see SecurityConfig)
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FavoriteDto>>> getFavorites() {
        return ResponseEntity.ok(ApiResponse.ok(favoriteService.getFavorites()));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<FavoritesSummaryDto>> getSummary() {
        return ResponseEntity.ok(ApiResponse.ok(favoriteService.getSummary()));
    }

    @PostMapping("/{productId}")
    public ResponseEntity<ApiResponse<FavoriteDto>> add(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok(favoriteService.add(productId)));
    }

    @PutMapping("/{productId}/target-price")
    public ResponseEntity<ApiResponse<FavoriteDto>> setTargetPrice(@PathVariable Long productId,
                                                                   @RequestBody TargetPriceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(favoriteService.setTargetPrice(productId, request.getTargetPrice())));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable Long productId) {
        favoriteService.remove(productId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/seen")
    public ResponseEntity<ApiResponse<Void>> markSeen() {
        favoriteService.markSeen();
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
