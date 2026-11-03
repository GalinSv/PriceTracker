package com.pricetracker.service.impl;

import com.pricetracker.dto.CatalogEntryDto;
import com.pricetracker.dto.CatalogPageDto;
import com.pricetracker.dto.CatalogStatusDto;
import com.pricetracker.entity.Store;
import com.pricetracker.enums.StoreStatus;
import com.pricetracker.exception.ResourceNotFoundException;
import com.pricetracker.exception.ScrapingException;
import com.pricetracker.repository.CatalogEntryRepository;
import com.pricetracker.repository.StoreRepository;
import com.pricetracker.scraper.HtmlUtils;
import com.pricetracker.scraper.PageFetcher;
import com.pricetracker.scraper.ScrapedProduct;
import com.pricetracker.scraper.StoreAdapter;
import com.pricetracker.scraper.StoreAdapters;
import com.pricetracker.service.CatalogService;
import com.pricetracker.service.ScraperService;
import com.pricetracker.util.TextUtils;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.parser.Parser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    // The link can be wrapped in CDATA: <loc><![CDATA[https://...]]></loc>
    private static final Pattern LOC = Pattern.compile("<loc>\\s*(?:<!\\[CDATA\\[)?\\s*([^<\\]\\s]+)\\s*(?:]]>)?\\s*</loc>");
    private static final Pattern URL_ENTRY = Pattern.compile("<url>(.*?)</url>", Pattern.DOTALL);
    // "Sitemap: https://..." lines in robots.txt
    private static final Pattern ROBOTS_TXT_SITEMAP =
            Pattern.compile("^\\s*sitemap:\\s*(\\S+)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Duration MAX_AGE = Duration.ofHours(24);
    private static final Duration PRICE_MAX_AGE = Duration.ofHours(6);
    private static final Duration NO_PRICE_MAX_AGE = Duration.ofHours(1);
    // <image:title>Лаптоп ASUS TUF Gaming A16 ...</image:title>: some stores give the product name with its picture
    private static final Pattern IMAGE_TITLE =
            Pattern.compile("<image:title>\\s*(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?\\s*</image:title>", Pattern.DOTALL);
    // "fx607vjr", "tu307", "1000xm5": letters and digits together
    private static final Pattern MODEL_CODE = Pattern.compile("(?=.*[a-z])(?=.*\\d)[a-z0-9]{4,}");
    // "144hz", "16gb", "512gb" are sizes, not model codes
    private static final Pattern NUMBER_WITH_UNIT = Pattern.compile("\\d+[a-z]{1,3}");
    private static final int PAGE_SIZE = 20;
    private static final int MAX_WORDS = 12;
    // Up to this many words, every word must be found; longer searches are scored
    private static final int MAX_REQUIRED_WORDS = 3;
    private static final int MODEL_CODE_WEIGHT = 3;
    private static final int BATCH_SIZE = 1000;

    // Bulk insert with plain SQL: JPA would save 1 000 000 links one by one.
    // A name read from a product page is kept over the one from the sitemap.
    private static final String UPSERT_SQL = """
            INSERT INTO catalog_entries (store_id, url, search_text, title, last_seen_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (url) DO UPDATE
            SET store_id = EXCLUDED.store_id, search_text = EXCLUDED.search_text,
                title = COALESCE(catalog_entries.title, EXCLUDED.title), last_seen_at = EXCLUDED.last_seen_at
            """;

    // For a tracked link (one that has an offer) the offer's name and price and its product's EAN and model are used
    private static final String ENTRY_COLUMNS = """
            e.url, e.search_text, e.title, e.price, e.currency, e.in_stock, e.checked_at,
            e.ean, e.brand, e.model_code, e.image_url, s.code, s.name,
            o.product_id, o.title AS offer_title, o.current_price AS offer_price,
            o.currency AS offer_currency, o.in_stock AS offer_in_stock,
            p.ean AS product_ean, p.brand AS product_brand, p.model_code AS product_model_code,
            p.image_url AS product_image_url
            """;
    private static final String ENTRY_TABLES = """
            FROM catalog_entries e
            JOIN stores s ON s.id = e.store_id
            LEFT JOIN offers o ON o.url = e.url
            LEFT JOIN products p ON p.id = o.product_id
            """;

    private final StoreRepository storeRepository;
    private final CatalogEntryRepository catalogEntryRepository;
    private final StoreAdapters storeAdapters;
    private final PageFetcher pageFetcher;
    private final ScraperService scraperService;
    private final JdbcTemplate jdbcTemplate;

    private final AtomicBoolean updating = new AtomicBoolean(false);
    private final ExecutorService background = Executors.newSingleThreadExecutor();

    @PreDestroy
    void stopBackgroundWork() {
        background.shutdownNow();
    }

    @Override
    public CatalogPageDto search(String query, List<String> storeCodes, int page) {
        List<String> words = queryWords(query);
        if (words.isEmpty()) {
            return new CatalogPageDto(List.of(), false);
        }
        List<String> stores = storeCodes == null ? List.of() : storeCodes;
        String storeFilter = stores.isEmpty() ? ""
                : " AND s.code IN (" + String.join(", ", Collections.nCopies(stores.size(), "?")) + ")";

        StringBuilder sql = new StringBuilder("SELECT ").append(ENTRY_COLUMNS);
        List<Object> params = new ArrayList<>();
        if (words.size() <= MAX_REQUIRED_WORDS) {
            // Short searches: every word must be there
            sql.append(" ").append(ENTRY_TABLES).append(" WHERE s.status = 'ACTIVE'").append(storeFilter);
            params.addAll(stores);
            for (String word : words) {
                sql.append(" AND e.search_text LIKE ?");
                params.add("%" + word + "%");
            }
            // Shorter links first: "logitech g502 hero" before "case for logitech g502 hero black ..."
            sql.append(" ORDER BY length(e.search_text), e.url");
        } else {
            // Longer searches, e.g. a name copied from a store, rarely match word for word.
            // Each word found gives points, a model code more, and half of all points is enough.
            StringBuilder score = new StringBuilder("(0");
            List<Object> scoreParams = new ArrayList<>();
            int maxScore = 0;
            for (String word : words) {
                int weight = isModelCode(word) ? MODEL_CODE_WEIGHT : 1;
                score.append(" + ").append(weight).append(" * (e.search_text LIKE ?)::int");
                scoreParams.add("%" + word + "%");
                maxScore += weight;
            }
            score.append(")");
            sql.append(", ").append(score).append(" AS score ").append(ENTRY_TABLES)
                    .append(" WHERE s.status = 'ACTIVE'").append(storeFilter)
                    .append(" AND ").append(score).append(" >= ?")
                    .append(" ORDER BY score DESC, length(e.search_text), e.url");
            params.addAll(scoreParams);
            params.addAll(stores);
            params.addAll(scoreParams);
            params.add((maxScore + 1) / 2);
        }

        // One more than a page, to know if there is a next one
        sql.append(" LIMIT ").append(PAGE_SIZE + 1).append(" OFFSET ").append((long) Math.max(page, 0) * PAGE_SIZE);
        List<CatalogEntryDto> found = jdbcTemplate.query(sql.toString(), this::toDto, params.toArray());
        boolean hasMore = found.size() > PAGE_SIZE;
        return new CatalogPageDto(hasMore ? found.subList(0, PAGE_SIZE) : found, hasMore);
    }

    // Single letters and digits ("0" from "16.0") would match almost every link
    private List<String> queryWords(String query) {
        List<String> words = TextUtils.searchWords(query);
        List<String> longer = words.stream().filter(word -> word.length() > 1).toList();
        return (longer.isEmpty() ? words : longer).stream().limit(MAX_WORDS).toList();
    }

    private boolean isModelCode(String word) {
        return MODEL_CODE.matcher(word).matches() && !NUMBER_WITH_UNIT.matcher(word).matches();
    }

    @Override
    public CatalogEntryDto check(String url) {
        String sql = "SELECT " + ENTRY_COLUMNS + " " + ENTRY_TABLES + " WHERE e.url = ?";
        CatalogEntryDto entry = jdbcTemplate.query(sql, this::toDto, url).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("This link is not in the catalog: " + url));
        if (entry.isChecked()) {
            return entry;
        }

        ScrapedProduct product = null;
        try {
            product = scraperService.scrape(url);
        } catch (ScrapingException e) {
            // E.g. a removed product. Remembered too, so we don't open the page on every search.
            log.debug("Could not check {}: {}", url, e.getMessage());
        }
        String modelCode = null;
        if (product != null) {
            modelCode = TextUtils.normalizeModelCode(product.getModelCode());
            entry.setName(product.getTitle());
            setPrice(entry, product.getPrice(), product.getCurrency(), product.getInStock());
            setIdentity(entry, TextUtils.truncate(product.getEan(), 20), TextUtils.truncate(product.getBrand(), 100),
                    modelCode, TextUtils.truncate(product.getImageUrl(), 1000));
        } else {
            setPrice(entry, null, null, null);
        }

        // Plain UPDATE, so we don't overwrite last_seen_at if the catalog is being updated at the same time.
        // COALESCE keeps what we already knew when the page could not be read this time.
        jdbcTemplate.update("""
                        UPDATE catalog_entries
                        SET title = COALESCE(?, title), price = ?, currency = ?, in_stock = ?, checked_at = ?,
                            ean = COALESCE(?, ean), brand = COALESCE(?, brand),
                            model_code = COALESCE(?, model_code), image_url = COALESCE(?, image_url)
                        WHERE url = ?
                        """,
                product == null ? null : TextUtils.truncate(product.getTitle(), 500),
                entry.getPrice(), entry.getCurrency(), entry.getInStock(), Timestamp.valueOf(LocalDateTime.now()),
                entry.getEan(), entry.getBrand(), modelCode, entry.getImageUrl(), url);
        return entry;
    }

    private CatalogEntryDto toDto(ResultSet rs, int rowNum) throws SQLException {
        CatalogEntryDto dto = new CatalogEntryDto();
        dto.setStoreCode(rs.getString("code"));
        dto.setStoreName(rs.getString("name"));
        dto.setUrl(rs.getString("url"));
        dto.setProductId(rs.getObject("product_id", Long.class));

        // A tracked link: the offer is refreshed every few hours anyway
        if (dto.getProductId() != null) {
            dto.setName(rs.getString("offer_title"));
            setPrice(dto, rs.getBigDecimal("offer_price"), rs.getString("offer_currency"),
                    rs.getObject("offer_in_stock", Boolean.class));
            setIdentity(dto, rs.getString("product_ean"), rs.getString("product_brand"),
                    rs.getString("product_model_code"), rs.getString("product_image_url"));
            return dto;
        }

        setIdentity(dto, rs.getString("ean"), rs.getString("brand"), rs.getString("model_code"),
                rs.getString("image_url"));
        String title = rs.getString("title");
        dto.setName(title != null ? title : displayName(rs.getString("search_text")));
        Timestamp checkedAt = rs.getTimestamp("checked_at");
        BigDecimal price = rs.getBigDecimal("price");
        if (checkedAt != null && isPriceFresh(checkedAt.toLocalDateTime(), price)) {
            setPrice(dto, price, rs.getString("currency"), rs.getObject("in_stock", Boolean.class));
        }
        return dto;
    }

    private void setPrice(CatalogEntryDto dto, BigDecimal price, String currency, Boolean inStock) {
        dto.setPrice(price);
        dto.setCurrency(currency);
        dto.setInStock(inStock);
        dto.setChecked(true);
    }

    private void setIdentity(CatalogEntryDto dto, String ean, String brand, String modelCode, String imageUrl) {
        dto.setEan(ean);
        dto.setBrand(brand);
        dto.setModelKey(brand != null && modelCode != null ? TextUtils.normalizeBrand(brand) + ":" + modelCode : null);
        dto.setImageUrl(imageUrl);
    }

    // A page without a price (a removed product, or the store was down) is opened again sooner
    private boolean isPriceFresh(LocalDateTime checkedAt, BigDecimal price) {
        Duration maxAge = price != null ? PRICE_MAX_AGE : NO_PRICE_MAX_AGE;
        return checkedAt.isAfter(LocalDateTime.now().minus(maxAge));
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
        boolean robotsTxt = adapter.sitemapUrl().endsWith("/robots.txt");
        int saved = 0;
        if (robotsTxt || root.contains("<sitemapindex")) {
            List<String> sitemaps = robotsTxt ? robotsTxtSitemaps(root) : locs(root);
            for (String sitemap : sitemaps) {
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
        // link -> product name or null. A map, because one SQL batch may not touch the same link twice.
        Map<String, String> links = new LinkedHashMap<>();
        Matcher entries = URL_ENTRY.matcher(sitemapXml);
        while (entries.find()) {
            String entry = entries.group(1);
            Matcher loc = LOC.matcher(entry);
            if (!loc.find()) {
                continue;
            }
            String url = loc.group(1).replace("&amp;", "&");
            if (url.length() <= 1000 && adapter.isProductEntry(url, entry)) {
                links.putIfAbsent(url, imageTitle(entry));
            }
        }

        Timestamp seen = Timestamp.valueOf(seenAt);
        List<Object[]> rows = links.entrySet().stream()
                .map(link -> new Object[]{store.getId(), link.getKey(), searchTextOf(link.getKey(), link.getValue()),
                        link.getValue(), seen})
                .toList();
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            jdbcTemplate.batchUpdate(UPSERT_SQL, rows.subList(from, Math.min(from + BATCH_SIZE, rows.size())));
        }
        return rows.size();
    }

    private String imageTitle(String entryXml) {
        Matcher matcher = IMAGE_TITLE.matcher(entryXml);
        if (!matcher.find()) {
            return null;
        }
        return TextUtils.truncate(HtmlUtils.blankToNull(Parser.unescapeEntities(matcher.group(1), false)), 500);
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

    private List<String> robotsTxtSitemaps(String robotsTxt) {
        List<String> result = new ArrayList<>();
        Matcher matcher = ROBOTS_TXT_SITEMAP.matcher(robotsTxt);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    // The words of the link's last part, plus the product name when the sitemap gives one
    private String searchTextOf(String url, String title) {
        String path = url.replaceFirst("^https?://[^/]+", "");
        String lastSegment = "";
        for (String segment : path.split("/")) {
            if (!segment.isEmpty()) {
                lastSegment = segment;
            }
        }
        String text = title == null ? lastSegment : lastSegment + " " + title;
        return TextUtils.truncate(String.join(" ", TextUtils.searchWords(text)), 1000);
    }

    private String displayName(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            return searchText;
        }
        return Character.toUpperCase(searchText.charAt(0)) + searchText.substring(1);
    }
}
