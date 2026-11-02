package com.pricetracker.scheduler;

import com.pricetracker.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Checks every hour (and shortly after start) whether a store catalog is older than a day
@Component
@RequiredArgsConstructor
public class CatalogScheduler {

    private final CatalogService catalogService;

    @Scheduled(initialDelayString = "${app.catalog.initial-delay-ms}",
            fixedDelayString = "${app.catalog.check-interval-ms}")
    public void updateOldCatalogs() {
        catalogService.startUpdate(false);
    }
}
