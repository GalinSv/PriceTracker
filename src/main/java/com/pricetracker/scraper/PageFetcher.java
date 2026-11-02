package com.pricetracker.scraper;

import com.pricetracker.exception.PageNotFoundException;
import com.pricetracker.exception.ScrapingException;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

@Component
public class PageFetcher {

    private static final int MAX_PAGE_SIZE = 10 * 1024 * 1024;
    // One PIC sitemap is ~10 MB (50 000 links)
    private static final int MAX_SITEMAP_SIZE = 100 * 1024 * 1024;
    private static final int SITEMAP_TIMEOUT_MS = 120_000;

    private final RobotsTxtChecker robotsTxtChecker;
    private final RateLimiter rateLimiter;
    private final String userAgent;
    private final int timeoutMs;

    public PageFetcher(RobotsTxtChecker robotsTxtChecker,
                       RateLimiter rateLimiter,
                       @Value("${app.scraper.user-agent}") String userAgent,
                       @Value("${app.scraper.timeout-ms}") int timeoutMs) {
        this.robotsTxtChecker = robotsTxtChecker;
        this.rateLimiter = rateLimiter;
        this.userAgent = userAgent;
        this.timeoutMs = timeoutMs;
    }

    public Document fetch(String url, long delayMs) {
        try {
            return download(url, delayMs, MAX_PAGE_SIZE, timeoutMs).parse();
        } catch (IOException e) {
            throw new ScrapingException("Could not read " + url + ": " + e.getMessage(), e);
        }
    }

    public String fetchText(String url, long delayMs) {
        return download(url, delayMs, MAX_SITEMAP_SIZE, SITEMAP_TIMEOUT_MS).body();
    }

    private Connection.Response download(String url, long delayMs, int maxSize, int timeout) {
        if (!robotsTxtChecker.isAllowed(url)) {
            throw new ScrapingException("The store's robots.txt does not allow this page: " + url);
        }
        rateLimiter.waitForTurn(URI.create(url).getHost(), delayMs);

        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .header("Accept-Language", "bg-BG,bg;q=0.9,en;q=0.8")
                    .timeout(timeout)
                    .maxBodySize(maxSize)
                    .followRedirects(true)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .execute();

            int status = response.statusCode();
            if (status == 404 || status == 410) {
                throw new PageNotFoundException("The store says this page does not exist: " + url);
            }
            if (status != 200) {
                throw new ScrapingException("The store returned HTTP " + status + " for " + url);
            }
            return response;
        } catch (IOException e) {
            throw new ScrapingException("Could not download " + url + ": " + e.getMessage(), e);
        }
    }
}
