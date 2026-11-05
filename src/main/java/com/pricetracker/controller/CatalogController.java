package com.pricetracker.controller;

import com.pricetracker.dto.ApiResponse;
import com.pricetracker.dto.CatalogEntryDto;
import com.pricetracker.dto.CatalogFiltersDto;
import com.pricetracker.dto.CatalogPageDto;
import com.pricetracker.dto.CatalogStatusDto;
import com.pricetracker.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    // Filters are optional and comma-separated: ?stores=pic,ozone&brands=Logitech&memory=256 GB.
    // page starts from 0; size is capped at 300. The first page also brings the filter values with counts.
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<CatalogPageDto>> search(@RequestParam(defaultValue = "") String query,
                                                              CatalogFiltersDto filters,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.search(query, filters, page, size, page == 0)));
    }

    // For search results that came without a fresh price
    @PostMapping("/check")
    public ResponseEntity<ApiResponse<CatalogEntryDto>> check(@RequestParam String url) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.check(url)));
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

    // Reads the filters of every link again, e.g. after brands.txt changed. Will be admin-only once we add Keycloak.
    @PostMapping("/facets")
    public ResponseEntity<ApiResponse<Void>> refreshFacets() {
        if (!catalogService.startFacetRefresh()) {
            throw new IllegalStateException("The catalog is already being updated");
        }
        return ResponseEntity.accepted().body(ApiResponse.ok(null, "Filter refresh started"));
    }
}
