package com.pricetracker.controller;

import com.pricetracker.dto.ApiResponse;
import com.pricetracker.dto.ProductDetailsDto;
import com.pricetracker.service.OfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
