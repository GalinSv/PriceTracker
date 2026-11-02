package com.pricetracker.scraper;

import com.pricetracker.exception.ScrapingException;
import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Downloads each store's robots.txt and tells if we may open a URL
@Component
public class RobotsTxtChecker {

    private static final Duration CACHE_TIME = Duration.ofHours(24);
    private static final String ROBOT_NAME = "pricetrackerbot";

    private final SimpleRobotRulesParser parser = new SimpleRobotRulesParser();
    private final Map<String, CachedRules> cache = new ConcurrentHashMap<>();
    private final String userAgent;
    private final int timeoutMs;

    public RobotsTxtChecker(@Value("${app.scraper.user-agent}") String userAgent,
                            @Value("${app.scraper.timeout-ms}") int timeoutMs) {
        this.userAgent = userAgent;
        this.timeoutMs = timeoutMs;
    }

    public boolean isAllowed(String url) {
        URI uri = URI.create(url);
        String origin = uri.getScheme() + "://" + uri.getHost();

        CachedRules cached = cache.get(origin);
        if (cached == null || cached.isExpired()) {
            cached = new CachedRules(download(origin), Instant.now());
            cache.put(origin, cached);
        }
        return cached.rules().isAllowed(url);
    }

    private BaseRobotRules download(String origin) {
        String robotsUrl = origin + "/robots.txt";
        try {
            Connection.Response response = Jsoup.connect(robotsUrl)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .followRedirects(true)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .execute();

            if (response.statusCode() != 200) {
                // No robots.txt (404) means everything is allowed; server errors mean "don't crawl now"
                return parser.failedFetch(response.statusCode());
            }
            return parser.parseContent(robotsUrl, response.bodyAsBytes(), response.contentType(), List.of(ROBOT_NAME));
        } catch (IOException e) {
            throw new ScrapingException("Could not download " + robotsUrl + ": " + e.getMessage(), e);
        }
    }

    private record CachedRules(BaseRobotRules rules, Instant downloadedAt) {
        boolean isExpired() {
            return downloadedAt.plus(CACHE_TIME).isBefore(Instant.now());
        }
    }
}
