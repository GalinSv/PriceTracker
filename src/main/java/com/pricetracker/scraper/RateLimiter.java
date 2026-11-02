package com.pricetracker.scraper;

import com.pricetracker.exception.ScrapingException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Makes sure we send at most one request per delay to the same store
@Component
public class RateLimiter {

    // host -> the earliest time (millis) the next request may start
    private final Map<String, Long> nextAllowed = new ConcurrentHashMap<>();

    public void waitForTurn(String host, long delayMs) {
        long[] waitMs = new long[1];

        // Reserve the next free slot for this host atomically, then sleep until it comes
        nextAllowed.compute(host, (h, next) -> {
            long now = System.currentTimeMillis();
            long start = (next == null || next < now) ? now : next;
            waitMs[0] = start - now;
            return start + delayMs;
        });

        if (waitMs[0] > 0) {
            try {
                Thread.sleep(waitMs[0]);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ScrapingException("Interrupted while waiting for " + host);
            }
        }
    }
}
