package com.pricetracker.service.impl;

import com.pricetracker.dto.CatalogEntryDto;
import com.pricetracker.dto.CatalogFacetsDto;
import com.pricetracker.dto.CatalogFiltersDto;
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
import com.pricetracker.util.FacetExtractor;
import com.pricetracker.util.TextUtils;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.parser.Parser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
    // "Load all results" asks for this many at once
    private static final int MAX_PAGE_SIZE = 300;
    private static final int MAX_EANS = 10;
    private static final int MAX_FACET_VALUES = 40;
    private static final int MAX_WORDS = 12;
    // Up to this many words, every word must be found; longer searches are scored
    private static final int MAX_REQUIRED_WORDS = 3;
    private static final int MODEL_CODE_WEIGHT = 3;
    private static final int BATCH_SIZE = 1000;

    // Bulk insert with plain SQL: JPA would save 1 000 000 links one by one.
    // A name read from a product page is kept over the one from the sitemap.
    private static final String UPSERT_SQL = """
            INSERT INTO catalog_entries (store_id, url, search_text, title, last_seen_at,
                                         facet_brand, facet_series, facet_memory, facet_color)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (url) DO UPDATE
            SET store_id = EXCLUDED.store_id, search_text = EXCLUDED.search_text,
                title = COALESCE(catalog_entries.title, EXCLUDED.title), last_seen_at = EXCLUDED.last_seen_at,
                facet_brand = EXCLUDED.facet_brand, facet_series = EXCLUDED.facet_series,
                facet_memory = EXCLUDED.facet_memory, facet_color = EXCLUDED.facet_color
            """;

    // For a tracked link (one that has an offer) the offer's name and price and its product's EAN and model are used
    private static final String ENTRY_COLUMNS = """
            e.url, e.search_text, e.title, e.price, e.currency, e.in_stock, e.checked_at,
            e.ean, e.brand, e.model_code, e.image_url, s.code, s.name,
            o.product_id, o.title AS offer_title, o.current_price AS offer_price,
            o.currency AS offer_currency, o.in_stock AS offer_in_stock,
            (SELECT string_agg(pe.ean, ',') FROM product_eans pe WHERE pe.product_id = p.id) AS product_eans,
            p.brand AS product_brand, p.model_code AS product_model_code, p.image_url AS product_image_url
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
    // For the filter counts of searches; more searches at once than threads just wait a little
    private final ExecutorService facetCounting = Executors.newFixedThreadPool(4);

    @PreDestroy
    void stopBackgroundWork() {
        background.shutdownNow();
        facetCounting.shutdownNow();
    }

    @Override
    public CatalogPageDto search(String query, CatalogFiltersDto filters, int page, int size, boolean withFacets) {
        List<String> words = TextUtils.searchWords(query).stream().limit(MAX_WORDS).toList();
        if (words.isEmpty()) {
            return new CatalogPageDto(List.of(), false, null);
        }
        CatalogFiltersDto chosen = filters == null ? new CatalogFiltersDto() : filters;
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        WordMatch match = wordMatch(words);
        Clause stores = Clause.in("s.code", chosen.getStores());
        Clause facetFilters = Clause.in("e.facet_brand", chosen.getBrands())
                .and(Clause.in("e.facet_series", chosen.getSeries()))
                .and(Clause.in("e.facet_memory", chosen.getMemory()))
                .and(Clause.in("e.facet_color", chosen.getColors()));

        String sql = "SELECT " + ENTRY_COLUMNS + match.select() + " " + ENTRY_TABLES
                + " WHERE s.status = 'ACTIVE'" + stores.sql() + facetFilters.sql() + match.where()
                + " ORDER BY " + match.orderBy()
                // One more than a page, to know if there is a next one
                + " LIMIT " + (pageSize + 1) + " OFFSET " + (long) Math.max(page, 0) * pageSize;
        List<Object> params = new ArrayList<>(match.selectParams());
        params.addAll(stores.params());
        params.addAll(facetFilters.params());
        params.addAll(match.whereParams());
        params.addAll(match.orderParams());

        // Both go through every link, so the filter counts run at the same time as the results
        CompletableFuture<CatalogFacetsDto> facets = withFacets
                ? CompletableFuture.supplyAsync(
                        () -> countFacets(match, stores, Clause.in("e.facet_brand", chosen.getBrands())), facetCounting)
                : CompletableFuture.completedFuture(null);
        List<CatalogEntryDto> found = jdbcTemplate.query(sql, this::toDto, params.toArray());
        boolean hasMore = found.size() > pageSize;
        return new CatalogPageDto(hasMore ? found.subList(0, pageSize) : found, hasMore, join(facets));
    }

    // Throws the facet query's own exception instead of a CompletionException around it
    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    // The part of the query that finds the search words, shared by the results and the filter counts
    private record WordMatch(String select, List<Object> selectParams, String where, List<Object> whereParams,
                             String orderBy, List<Object> orderParams) {
    }

    // "AND column IN (?, ?)" with its values; empty when nothing is chosen
    private record Clause(String sql, List<Object> params) {
        static Clause in(String column, List<String> values) {
            if (values == null || values.isEmpty()) {
                return new Clause("", List.of());
            }
            return new Clause(" AND " + column + " IN (" + String.join(", ", Collections.nCopies(values.size(), "?")) + ")",
                    List.copyOf(values));
        }

        Clause and(Clause other) {
            List<Object> both = new ArrayList<>(params);
            both.addAll(other.params());
            return new Clause(sql + other.sql(), both);
        }
    }

    private WordMatch wordMatch(List<String> words) {
        List<Object> orderParams = new ArrayList<>();
        String relevance = accessoriesLast(words, orderParams) + ", " + phraseFirst(words, orderParams)
                // Then shorter links: "logitech g502 hero" before "case for logitech g502 hero black ..."
                + ", length(e.search_text), e.url";

        if (words.size() <= MAX_REQUIRED_WORDS) {
            // Short searches: every word must be there
            StringBuilder where = new StringBuilder();
            List<Object> whereParams = new ArrayList<>();
            for (int i = 0; i < words.size(); i++) {
                where.append(" AND ").append(wordCondition(words, i, whereParams));
            }
            return new WordMatch("", List.of(), where.toString(), whereParams, relevance, orderParams);
        }

        // Longer searches, e.g. a name copied from a store, rarely match word for word.
        // Each word found gives points, a model code more, and half of all points is enough.
        // The words before the first size name the model: "samsung galaxy s26 ultra" of
        // "samsung galaxy s26 ultra 5g 256gb 12gb ram dual". Links with more of them come first, so a
        // "galaxy s24 ultra 5g 256gb 12gb ram dual" can't pass an S26 Ultra whose store doesn't write "ram dual".
        int nameWords = nameWordCount(words);
        boolean nameFirst = nameWords > 0 && nameWords < words.size();
        StringBuilder score = new StringBuilder("(0");
        StringBuilder nameScore = new StringBuilder("(0");
        List<Object> scoreParams = new ArrayList<>();
        List<Object> nameParams = new ArrayList<>();
        int[] weights = new int[words.size()];
        int maxScore = 0;
        for (int i = 0; i < words.size(); i++) {
            int weight = isModelCode(words.get(i)) ? MODEL_CODE_WEIGHT : 1;
            weights[i] = weight;
            score.append(" + ").append(weight).append(" * (").append(wordCondition(words, i, scoreParams))
                    .append(")::int");
            if (nameFirst && i < nameWords) {
                nameScore.append(" + ").append(weight).append(" * (").append(wordCondition(words, i, nameParams))
                        .append(")::int");
            }
            maxScore += weight;
        }
        score.append(")");
        nameScore.append(")");

        String select = ", " + score + " AS score";
        List<Object> selectParams = new ArrayList<>(scoreParams);
        String orderBy = "score DESC, " + relevance;
        if (nameFirst) {
            select += ", " + nameScore + " AS name_score";
            selectParams.addAll(nameParams);
            orderBy = "name_score DESC, " + orderBy;
        }
        int threshold = (maxScore + 1) / 2;
        List<Object> whereParams = new ArrayList<>(scoreParams);
        whereParams.add(threshold);
        String where = " AND " + score + " >= ?" + indexFilter(words, weights, threshold, whereParams);
        return new WordMatch(select, selectParams, where, whereParams, orderBy, orderParams);
    }

    // Accessories after the product itself: links with "za" or "for" before the first search word, like
    // "kalaf za samsung galaxy buds 4 pro" for "samsung galaxy buds 4 pro". They have all the words too,
    // often word for word. A search that starts with the accessory ("kalaf za iphone 17") isn't affected,
    // as its first word comes before the "za".
    private String accessoriesLast(List<String> words, List<Object> params) {
        String firstWord = " " + words.get(0);
        params.add(firstWord);
        params.add(firstWord);
        return "(strpos(' ' || e.search_text || ' ', ' za ') BETWEEN 1 AND strpos(' ' || e.search_text, ?)"
                + " OR strpos(' ' || e.search_text || ' ', ' for ') BETWEEN 1 AND strpos(' ' || e.search_text, ?)) ASC";
    }

    // Links with the words next to each other, e.g. "jbl flip 7", before the rest. Short words may be glued to
    // the word before them, as most stores write "galaxy buds4 pro" for "galaxy buds 4 pro".
    private String phraseFirst(List<String> words, List<Object> params) {
        StringBuilder glued = new StringBuilder(words.get(0));
        for (String word : words.subList(1, words.size())) {
            glued.append(isShortWord(word) ? "" : " ").append(word);
        }
        List<String> phrases = List.of(String.join(" ", words), glued.toString()).stream().distinct().toList();
        phrases.forEach(phrase -> params.add("%" + phrase + "%"));
        return "(" + String.join(" OR ", Collections.nCopies(phrases.size(), "e.search_text LIKE ?")) + ") DESC";
    }

    // The points above have to be counted link by link, through all of them. A link with enough points must
    // have at least one of the longest words, taken until the words left out are worth less than the
    // threshold, and "has one of these words" can use the search index (db/catalog-search.sql) to skip the
    // links that don't. It changes no result, only the speed. Words under 3 letters can't use the index, so if
    // they alone could reach the threshold there is nothing to narrow down by.
    private String indexFilter(List<String> words, int[] weights, int threshold, List<Object> params) {
        List<Integer> longestFirst = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            if (words.get(i).length() >= 3) {
                longestFirst.add(i);
            }
        }
        longestFirst.sort(Comparator.comparingInt((Integer i) -> words.get(i).length()).reversed());

        List<Object> anyOf = new ArrayList<>();
        int leftOut = 0;
        for (int weight : weights) {
            leftOut += weight;
        }
        for (int i : longestFirst) {
            if (leftOut < threshold) {
                break;
            }
            anyOf.add("%" + words.get(i) + "%");
            leftOut -= weights[i];
        }
        if (leftOut >= threshold) {
            return "";
        }
        params.addAll(anyOf);
        return " AND (" + String.join(" OR ", Collections.nCopies(anyOf.size(), "e.search_text LIKE ?")) + ")";
    }

    // How many words come before the first size like "5g", "256gb" or "144hz"; all of them if there is none
    private int nameWordCount(List<String> words) {
        for (int i = 0; i < words.size(); i++) {
            if (NUMBER_WITH_UNIT.matcher(words.get(i)).matches()) {
                return i;
            }
        }
        return words.size();
    }

    // How many links of the search have each brand, series, memory and color. The other filters are left out,
    // so the counts stay the same while you tick values and a second brand can be added.
    // Only the series follow the chosen brands, so "Razer" shows Razer's series.
    private CatalogFacetsDto countFacets(WordMatch match, Clause stores, Clause brands) {
        String brandLinks = brands.sql().isEmpty() ? "count(*)" : "count(*) FILTER (WHERE true" + brands.sql() + ")";
        String sql = """
                SELECT e.facet_brand, e.facet_series, e.facet_memory, e.facet_color,
                       GROUPING(e.facet_brand) AS by_brand, GROUPING(e.facet_series) AS by_series,
                       GROUPING(e.facet_memory) AS by_memory, count(*) AS links,
                """ + brandLinks + """
                 AS brand_links
                FROM catalog_entries e
                JOIN stores s ON s.id = e.store_id
                WHERE s.status = 'ACTIVE'
                """ + stores.sql() + match.where() + """
                 GROUP BY GROUPING SETS ((e.facet_brand), (e.facet_series), (e.facet_memory), (e.facet_color))
                """;
        List<Object> params = new ArrayList<>(brands.params());
        params.addAll(stores.params());
        params.addAll(match.whereParams());

        Map<String, List<CatalogFacetsDto.FacetValue>> byFacet = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            String facet = rs.getInt("by_brand") == 0 ? "facet_brand"
                    : rs.getInt("by_series") == 0 ? "facet_series"
                    : rs.getInt("by_memory") == 0 ? "facet_memory"
                    : "facet_color";
            String value = rs.getString(facet);
            long links = rs.getLong(facet.equals("facet_series") ? "brand_links" : "links");
            if (value != null && links > 0) {
                byFacet.computeIfAbsent(facet, key -> new ArrayList<>())
                        .add(new CatalogFacetsDto.FacetValue(value, links));
            }
        }, params.toArray());
        return new CatalogFacetsDto(topValues(byFacet.get("facet_brand")), topValues(byFacet.get("facet_series")),
                topValues(byFacet.get("facet_memory")), topValues(byFacet.get("facet_color")));
    }

    private List<CatalogFacetsDto.FacetValue> topValues(List<CatalogFacetsDto.FacetValue> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .sorted(Comparator.comparingLong(CatalogFacetsDto.FacetValue::getCount).reversed())
                .limit(MAX_FACET_VALUES)
                .toList();
    }

    // Short words like the "7" in "flip 7" must stand alone ("flip 7", "256gb") or be glued to the word
    // before them ("flip7"). As a part of any word they would match almost every link ("... 786543").
    private String wordCondition(List<String> words, int index, List<Object> params) {
        String word = words.get(index);
        if (!isShortWord(word)) {
            params.add("%" + word + "%");
            return "e.search_text LIKE ?";
        }
        String gluedTo = index == 0 ? "" : "|" + words.get(index - 1);
        String after = Character.isDigit(word.charAt(0)) ? "([^0-9]|$)" : "([^a-z]|$)";
        // The regex is slow on millions of links, so the cheap LIKE first skips links that don't have the word at all
        params.add("%" + word + "%");
        params.add("(^| " + gluedTo + ")" + word + after);
        return "(e.search_text LIKE ? AND e.search_text ~ ?)";
    }

    // One letter, or a number of up to three digits: "x", "7", "256"
    private boolean isShortWord(String word) {
        return word.length() == 1 || (word.length() <= 3 && word.chars().allMatch(Character::isDigit));
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
        String eans = null;
        if (product != null) {
            modelCode = TextUtils.normalizeModelCode(product.getModelCode());
            // Ten whole barcodes fit in the column, so none is ever cut in half
            eans = product.getEans().isEmpty() ? null
                    : String.join(",", product.getEans().stream().limit(MAX_EANS).toList());
            entry.setName(product.getTitle());
            setPrice(entry, product.getPrice(), product.getCurrency(), product.getInStock());
            setIdentity(entry, eans, TextUtils.truncate(product.getBrand(), 100), modelCode,
                    TextUtils.truncate(product.getImageUrl(), 1000));
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
                eans, entry.getBrand(), modelCode, entry.getImageUrl(), url);
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
            setIdentity(dto, rs.getString("product_eans"), rs.getString("product_brand"),
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

    // eans: comma-separated, as kept in the database
    private void setIdentity(CatalogEntryDto dto, String eans, String brand, String modelCode, String imageUrl) {
        dto.setEans(eans == null ? List.of() : List.of(eans.split(",")));
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

    @Override
    public boolean startFacetRefresh() {
        if (!updating.compareAndSet(false, true)) {
            return false;
        }
        background.submit(() -> {
            try {
                refreshFacets();
            } catch (RuntimeException e) {
                log.error("Facet refresh failed", e);
            } finally {
                updating.set(false);
            }
        });
        return true;
    }

    // Reads the filters of every link again from its search words, without downloading the catalogs
    private void refreshFacets() {
        FacetExtractor facets = FacetExtractor.load(brandsFromPages());
        long lastId = 0;
        int done = 0;
        while (true) {
            List<Object[]> rows = jdbcTemplate.query(
                    "SELECT id, search_text FROM catalog_entries WHERE id > ? ORDER BY id LIMIT " + BATCH_SIZE * 5,
                    (rs, rowNum) -> {
                        FacetExtractor.Facets found = facets.extract(rs.getString("search_text"));
                        return new Object[]{found.brand(), found.series(), found.memory(), found.color(),
                                rs.getLong("id")};
                    }, lastId);
            if (rows.isEmpty()) {
                break;
            }
            jdbcTemplate.batchUpdate("""
                    UPDATE catalog_entries SET facet_brand = ?, facet_series = ?, facet_memory = ?, facet_color = ?
                    WHERE id = ?
                    """, rows);
            lastId = (Long) rows.get(rows.size() - 1)[4];
            done += rows.size();
        }
        log.info("Filters read again for {} catalog links", done);
    }

    private void updateCatalogs(boolean force) {
        FacetExtractor facets = FacetExtractor.load(brandsFromPages());
        for (Store store : storeRepository.findAll()) {
            Optional<StoreAdapter> adapter = searchableAdapter(store);
            if (adapter.isEmpty() || (!force && isFresh(store))) {
                continue;
            }
            try {
                int links = updateCatalog(store, adapter.get(), facets);
                log.info("Catalog of {} updated: {} product links", store.getName(), links);
            } catch (RuntimeException e) {
                log.warn("Could not update the catalog of {}: {}", store.getName(), e.getMessage());
            }
        }
    }

    // Brands as product pages write them, to spell the brand filter right
    private List<String> brandsFromPages() {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT brand FROM catalog_entries WHERE brand IS NOT NULL
                UNION SELECT DISTINCT brand FROM products WHERE brand IS NOT NULL
                """, String.class);
    }

    private int updateCatalog(Store store, StoreAdapter adapter, FacetExtractor facets) {
        LocalDateTime startedAt = LocalDateTime.now();
        long delay = store.getCrawlDelayMs();

        String root = pageFetcher.fetchText(adapter.sitemapUrl(), delay);
        boolean robotsTxt = adapter.sitemapUrl().endsWith("/robots.txt");
        int saved = 0;
        if (robotsTxt || root.contains("<sitemapindex")) {
            List<String> sitemaps = robotsTxt ? robotsTxtSitemaps(root) : locs(root);
            for (String sitemap : sitemaps) {
                if (adapter.isProductSitemap(sitemap)) {
                    saved += saveLinks(store, adapter, pageFetcher.fetchText(sitemap, delay), startedAt, facets);
                }
            }
        } else {
            saved = saveLinks(store, adapter, root, startedAt, facets);
        }

        // Only reached if every sitemap was downloaded: links we didn't see are no longer sold
        jdbcTemplate.update("DELETE FROM catalog_entries WHERE store_id = ? AND last_seen_at < ?",
                store.getId(), Timestamp.valueOf(startedAt));
        // Plain UPDATE, so we don't overwrite fields the price refresh may be changing at the same time
        jdbcTemplate.update("UPDATE stores SET catalog_updated_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now()), store.getId());
        return saved;
    }

    private int saveLinks(Store store, StoreAdapter adapter, String sitemapXml, LocalDateTime seenAt,
                          FacetExtractor facets) {
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
                .map(link -> {
                    String searchText = searchTextOf(link.getKey(), link.getValue());
                    FacetExtractor.Facets found = facets.extract(searchText);
                    return new Object[]{store.getId(), link.getKey(), searchText, link.getValue(), seen,
                            found.brand(), found.series(), found.memory(), found.color()};
                })
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
        String words = decode(lastSegment).replaceFirst("(?i)\\.html?$", "");
        String text = title == null ? words : words + " " + title;
        return TextUtils.truncate(String.join(" ", TextUtils.searchWords(text)), 1000);
    }

    // Some stores put Cyrillic in their links as %D0%BC..., which would otherwise become junk words
    private String decode(String segment) {
        try {
            return URLDecoder.decode(segment, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return segment;
        }
    }

    private String displayName(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            return searchText;
        }
        return Character.toUpperCase(searchText.charAt(0)) + searchText.substring(1);
    }
}
