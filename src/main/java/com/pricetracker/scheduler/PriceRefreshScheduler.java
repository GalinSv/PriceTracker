package com.pricetracker.scheduler;

import com.pricetracker.service.OfferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceRefreshScheduler {

    private final OfferService offerService;

    @Scheduled(cron = "${app.scraper.refresh-cron}")
    public void refreshPrices() {
        try {
            offerService.refreshAll();
        } catch (IllegalStateException e) {
            log.info("Scheduled price refresh skipped: {}", e.getMessage());
        }
    }
}
