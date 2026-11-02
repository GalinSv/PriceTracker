package com.pricetracker.controller;

import com.pricetracker.dto.ApiResponse;
import com.pricetracker.dto.PriceSnapshotDto;
import com.pricetracker.dto.ProductDetailsDto;
import com.pricetracker.dto.RefreshResultDto;
import com.pricetracker.service.OfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/offers")
@RequiredArgsConstructor
public class OfferController {

    private final OfferService offerService;

    // "Add by link"
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<ProductDetailsDto>> importFromUrl(@RequestParam String url) {
        return ResponseEntity.ok(ApiResponse.ok(offerService.importFromUrl(url), "Offer saved"));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<List<PriceSnapshotDto>>> getHistory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(offerService.getHistory(id)));
    }

    // Same as the scheduled refresh, but now. Will be admin-only once we add Keycloak.
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResultDto>> refreshAll() {
        return ResponseEntity.ok(ApiResponse.ok(offerService.refreshAll(), "Prices refreshed"));
    }
}
