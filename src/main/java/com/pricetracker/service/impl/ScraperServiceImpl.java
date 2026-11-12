package com.pricetracker.service.impl;

import com.pricetracker.entity.Store;
import com.pricetracker.enums.StoreStatus;
import com.pricetracker.exception.NoPriceException;
import com.pricetracker.exception.ScrapingException;
import com.pricetracker.repository.StoreRepository;
import com.pricetracker.scraper.PageFetcher;
import com.pricetracker.scraper.ScrapedProduct;
import com.pricetracker.scraper.StoreAdapter;
import com.pricetracker.scraper.StoreAdapters;
import com.pricetracker.service.ScraperService;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;

@Service
@RequiredArgsConstructor
public class ScraperServiceImpl implements ScraperService {

    private final StoreRepository storeRepository;
    private final PageFetcher pageFetcher;
    private final StoreAdapters storeAdapters;

    @Override
    public ScrapedProduct scrape(String url) {
        URI uri = parseUrl(url);
        Store store = findStore(uri.getHost());

        if (store.getStatus() != StoreStatus.ACTIVE) {
            throw new IllegalArgumentException(store.getName() + " is " + store.getStatus()
                    + (store.getLastError() != null ? " (" + store.getLastError() + ")" : ""));
        }
        StoreAdapter adapter = storeAdapters.find(store.getCode())
                .orElseThrow(() -> new IllegalArgumentException("Reading products from " + store.getName() + " is not supported yet"));

        Document page = pageFetcher.fetch(uri.toString(), store.getCrawlDelayMs());
        ScrapedProduct product = adapter.parseProduct(page);

        if (product.getTitle() == null || product.getPrice() == null) {
            throw new ScrapingException("Could not read the name or the price from " + uri);
        }
        if (product.getPrice().signum() <= 0) {
            throw new NoPriceException("The store shows no price for " + uri);
        }
        product.setStoreCode(store.getCode());
        product.setUrl(canonicalUrl(page, uri));
        if (product.getCurrency() == null) {
            product.setCurrency("EUR");
        }
        if (product.getInStock() == null) {
            product.setInStock(true);   // the store doesn't say otherwise
        }
        return product;
    }

    private URI parseUrl(String url) {
        try {
            URI uri = new URI(url == null ? "" : url.trim());
            if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
                throw new IllegalArgumentException("Not a valid link: " + url);
            }
            // Drop "#reviews" and similar; the server never sees it anyway
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Not a valid link: " + url);
        }
    }

    private Store findStore(String host) {
        String wanted = withoutWww(host);
        return storeRepository.findAll().stream()
                .filter(store -> wanted.equals(withoutWww(URI.create(store.getBaseUrl()).getHost())))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("This store is not in our list: " + host));
    }

    // The same product can be opened with tracking parameters (?utm_source=...).
    // Stores mark the "real" address with <link rel="canonical">, so one product = one offer.
    private String canonicalUrl(Document page, URI requested) {
        Element canonical = page.selectFirst("link[rel=canonical]");
        if (canonical != null) {
            String href = canonical.absUrl("href");
            if (!href.isEmpty() && withoutWww(URI.create(href).getHost()).equals(withoutWww(requested.getHost()))) {
                return href;
            }
        }
        return requested.getScheme() + "://" + requested.getAuthority() + requested.getPath();
    }

    private String withoutWww(String host) {
        String lower = host == null ? "" : host.toLowerCase();
        return lower.startsWith("www.") ? lower.substring(4) : lower;
    }
}
