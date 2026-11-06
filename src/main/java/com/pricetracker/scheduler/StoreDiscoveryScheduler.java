package com.pricetracker.scheduler;

import com.pricetracker.service.StoreDiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Once a day: looks for new stores that sell the tracked products
@Component
@RequiredArgsConstructor
public class StoreDiscoveryScheduler {

    private final StoreDiscoveryService storeDiscoveryService;

    @Scheduled(cron = "${app.discovery.cron}")
    public void findNewStores() {
        storeDiscoveryService.findNewStoresForAll();
    }
}
