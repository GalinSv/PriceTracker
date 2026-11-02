package com.pricetracker.service.impl;

import com.pricetracker.dto.CatalogEntryDto;
import com.pricetracker.dto.CatalogStatusDto;
import com.pricetracker.entity.Store;
import com.pricetracker.enums.StoreStatus;
import com.pricetracker.repository.CatalogEntryRepository;
import com.pricetracker.repository.StoreRepository;
import com.pricetracker.scraper.PageFetcher;
import com.pricetracker.scraper.StoreAdapter;
import com.pricetracker.scraper.StoreAdapters;
import com.pricetracker.service.CatalogService;
import com.pricetracker.util.TextUtils;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Keeps a list of every product link the stores publish in their sitemaps,
// so we can search by word without opening thousands of pages.
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogService {

    private static final Pattern LOC = Pattern.compile("<loc>\\s*([^<\\s]+)\\s*</loc>");
    private static final Duration MAX_AGE = Duration.ofHours(24);
    private static final int MAX_RESULTS = 50;
    private static final int MAX_WORDS = 8;
    private static final int BATCH_SIZE = 1000;

    // Bulk insert with plain SQL: JPA would save 1 000 000 links one by one
    private static final String UPSERT_SQL = """
            INSERT INTO catalog_entries (store_id, url, search_text, last_seen_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (url) DO UPDATE
            SET store_id = EXCLUDED.store_id, search_text = EXCLUDED.search_text, last_seen_at = EXCLUDED.last_seen_at
            """;

    private final StoreRepository storeRepository;
    private final CatalogEntryRepository catalogEntryRepository;
    private final StoreAdapters storeAdapters;
    private final PageFetcher pageFetcher;
    private final JdbcTemplate jdbcTemplate;

    private final AtomicBoolean updating = new AtomicBoolean(false);
    private final ExecutorService background = Executors.newSingleThreadExecutor();

    @PreDestroy
    void stopBackgroundWork() {
        background.shutdownNow();
    }

    @Override
    public List<CatalogEntryDto> search(String query) {
        List<String> words = TextUtils.searchWords(query).stream().limit(MAX_WORDS).toList();
        if (words.isEmpty()) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
                SELECT e.url, e.search_text, s.code, s.name, o.product_id
                FROM catalog_entries e
                JOIN stores s ON s.id = e.store_id
                LEFT JOIN offers o ON o.url = e.url
                WHERE s.status = 'ACTIVE'
                """);
        words.forEach(word -> sql.append(" AND e.search_text LIKE ?"));
        // Shorter links first: "logitech g502 hero" before "case for logitech g502 hero black ..."
        sql.append(" ORDER BY length(e.search_text), e.url LIMIT ").append(MAX_RESULTS);

        Object[] params = words.stream().map(word -> "%" + word + "%").toArray();
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new CatalogEntryDto(
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("url"),
                displayName(rs.getString("search_text")),
                rs.getObject("product_id", Long.class)), params);
    }

    @Override
    public CatalogStatusDto getStatus() {
        List<CatalogStatusDto.StoreCatalog> stores = new ArrayList<>();
        for (Store store : storeRepository.findAll()) {
            if (searchableAdapter(store).isPresent()) {
                stores.add(new CatalogStatusDto.StoreCatalog(store.getCode(), store.getName(),
                        catalogEntryRepository.countByStoreId(store.getId()), store.getCatalogUpdatedAt()));
            }
        }
        return new CatalogStatusDto(updating.get(), stores);
    }

    @Override
    public boolean startUpdate(boolean force) {
        if (!updating.compareAndSet(false, true)) {
            return false;
        }
        background.submit(() -> {
            try {
                updateCatalogs(force);
            } catch (RuntimeException e) {
                log.error("Catalog update failed", e);
            } finally {
                updating.set(false);
            }
        });
        return true;
    }

    private void updateCatalogs(boolean force) {
        for (Store store : storeRepository.findAll()) {
            Optional<StoreAdapter> adapter = searchableAdapter(store);
            if (adapter.isEmpty() || (!force && isFresh(store))) {
                continue;
            }
            try {
                int links = updateCatalog(store, adapter.get());
                log.info("Catalog of {} updated: {} product links", store.getName(), links);
            } catch (RuntimeException e) {
                log.warn("Could not update the catalog of {}: {}", store.getName(), e.getMessage());
            }
        }
    }

    private int updateCatalog(Store store, StoreAdapter adapter) {
        LocalDateTime startedAt = LocalDateTime.now();
        long delay = store.getCrawlDelayMs();

        String root = pageFetcher.fetchText(adapter.sitemapUrl(), delay);
        int saved = 0;
        if (root.contains("<sitemapindex")) {
            for (String sitemap : locs(root)) {
                if (adapter.isProductSitemap(sitemap)) {
                    saved += saveLinks(store, adapter, pageFetcher.fetchText(sitemap, delay), startedAt);
                }
            }
        } else {
            saved = saveLinks(store, adapter, root, startedAt);
        }

        // Only reached if every sitemap was downloaded: links we didn't see are no longer sold
        jdbcTemplate.update("DELETE FROM catalog_entries WHERE store_id = ? AND last_seen_at < ?",
                store.getId(), Timestamp.valueOf(startedAt));
        // Plain UPDATE, so we don't overwrite fields the price refresh may be changing at the same time
        jdbcTemplate.update("UPDATE stores SET catalog_updated_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now()), store.getId());
        return saved;
    }

    private int saveLinks(Store store, StoreAdapter adapter, String sitemapXml, LocalDateTime seenAt) {
        // A set, because one SQL batch may not touch the same link twice
        Set<String> urls = new LinkedHashSet<>();
        for (String url : locs(sitemapXml)) {
            if (url.length() <= 1000 && adapter.isProductUrl(url)) {
                urls.add(url);
            }
        }

        Timestamp seen = Timestamp.valueOf(seenAt);
        List<Object[]> rows = urls.stream()
                .map(url -> new Object[]{store.getId(), url, searchTextOf(url), seen})
                .toList();
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            jdbcTemplate.batchUpdate(UPSERT_SQL, rows.subList(from, Math.min(from + BATCH_SIZE, rows.size())));
        }
        return rows.size();
    }

    private Optional<StoreAdapter> searchableAdapter(Store store) {
        if (store.getStatus() != StoreStatus.ACTIVE) {
            return Optional.empty();
        }
        return storeAdapters.find(store.getCode()).filter(adapter -> adapter.sitemapUrl() != null);
    }

    private boolean isFresh(Store store) {
        return store.getCatalogUpdatedAt() != null
                && store.getCatalogUpdatedAt().isAfter(LocalDateTime.now().minus(MAX_AGE));
    }

    private List<String> locs(String xml) {
        List<String> result = new ArrayList<>();
        Matcher matcher = LOC.matcher(xml);
        while (matcher.find()) {
            result.add(matcher.group(1).replace("&amp;", "&"));
        }
        return result;
    }


    private String searchTextOf(String url) {
        String path = url.replaceFirst("^https?://[^/]+", "");
        String lastSegment = "";
        for (String segment : path.split("/")) {
            if (!segment.isEmpty()) {
                lastSegment = segment;
            }
        }
        return TextUtils.truncate(String.join(" ", TextUtils.searchWords(lastSegment)), 1000);
    }

    private String displayName(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            return searchText;
        }
        return Character.toUpperCase(searchText.charAt(0)) + searchText.substring(1);
    }
}
