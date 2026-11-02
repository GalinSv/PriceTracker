package com.pricetracker.controller;

import com.pricetracker.dto.ApiResponse;
import com.pricetracker.dto.CatalogEntryDto;
import com.pricetracker.dto.CatalogStatusDto;
import com.pricetracker.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<CatalogEntryDto>>> search(@RequestParam(defaultValue = "") String query) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.search(query)));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<CatalogStatusDto>> getStatus() {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.getStatus()));
    }

    // Downloads all catalogs again, even if they are fresh. Will be admin-only once we add Keycloak.
    @PostMapping("/update")
    public ResponseEntity<ApiResponse<Void>> update() {
        if (!catalogService.startUpdate(true)) {
            throw new IllegalStateException("The catalog is already being updated");
        }
        return ResponseEntity.accepted().body(ApiResponse.ok(null, "Catalog update started"));
    }
}
