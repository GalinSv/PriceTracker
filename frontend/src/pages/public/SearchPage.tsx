import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Box,
    Button,
    Chip,
    InputAdornment,
    LinearProgress,
    Pagination,
    TextField,
    Typography,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import SearchIcon from "@mui/icons-material/Search";

import CatalogFilterBar from "../../components/catalog/CatalogFilterBar";
import ProductGroupCard from "../../components/catalog/ProductGroupCard";
import ProductCard from "../../components/product/ProductCard";
import { checkCatalogEntry, getCatalogStatus, searchCatalog } from "../../services/catalogService";
import { importGroup, importOffer } from "../../services/offerService";
import { searchProducts } from "../../services/productService";
import type { CatalogEntry, ProductSummary, StoreCatalog } from "../../types/api";
import { brandOptions, filterAndSort, groupEntries, NO_FILTERS } from "../../utils/catalogGroups";
import type { CatalogFilters, ProductGroup } from "../../utils/catalogGroups";
import { notify } from "../../utils/notify";

const EXAMPLES = ["logitech g502", "слушалки sony", "ssd 1tb", "iphone 16"];
const PRODUCTS_PER_PAGE = 20;
// The most the server gives at once
const LOAD_ALL_SIZE = 300;

type SearchResults = {
    query: string;
    // The store filter the catalog was searched with, e.g. "pic,ozone"
    storesKey: string;
    products: ProductSummary[];
    catalog: CatalogEntry[];
    hasMore: boolean;
    // "Load all" was used; hasMore then means there were more than LOAD_ALL_SIZE links
    loadedAll: boolean;
};

function SearchPage() {
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const query = searchParams.get("q") ?? "";

    const [input, setInput] = useState(query);
    const [results, setResults] = useState<SearchResults | null>(null);
    const [filters, setFilters] = useState<CatalogFilters>(NO_FILTERS);
    const [stores, setStores] = useState<StoreCatalog[]>([]);
    const [importingUrl, setImportingUrl] = useState<string | null>(null);
    const [loadingAll, setLoadingAll] = useState(false);
    // The product block being opened
    const [openingKey, setOpeningKey] = useState<string | null>(null);
    // From 1
    const [viewPage, setViewPage] = useState(1);
    // Grows with every new search, so the price requests of an older search stop
    const searchNumber = useRef(0);
    // Links whose price was already asked for in this search
    const requestedUrls = useRef(new Set<string>());
    const resultsTop = useRef<HTMLHeadingElement>(null);

    const storesKey = filters.stores.join(",");
    // Still loading while the shown results belong to an older query
    const loading = query !== "" && results?.query !== query;
    // A new store filter: the old results stay on screen until the new ones come
    const refreshing = !loading && results != null && results.storesKey !== storesKey;
    const products = results?.products ?? [];
    const catalog = results?.catalog ?? [];

    const pendingCount = catalog.filter((entry) => !entry.checked).length;
    // Results join a product block once their page was opened and we know their EAN or model
    const groups = groupEntries(catalog.filter((entry) => entry.checked));
    const visibleGroups = filterAndSort(groups, filters);
    const inStockGroups = visibleGroups.filter((group) => group.inStockCount > 0);
    const soldOutGroups = visibleGroups.filter((group) => group.inStockCount === 0);
    const pageCount = Math.max(1, Math.ceil(inStockGroups.length / PRODUCTS_PER_PAGE));
    // Blocks merge while prices load, so there can be fewer pages than a moment ago
    const currentPage = Math.min(viewPage, pageCount);
    const pageGroups = inStockGroups.slice((currentPage - 1) * PRODUCTS_PER_PAGE, currentPage * PRODUCTS_PER_PAGE);

    useEffect(() => {
        getCatalogStatus()
            .then((status) => setStores(status.stores))
            .catch((error) => notify.error(error, "Магазините не можаха да бъдат заредени"));
    }, []);

    useEffect(() => {
        if (!query) return;
        searchNumber.current += 1;
        requestedUrls.current = new Set();
        let cancelled = false;
        const storeCodes = storesKey ? storesKey.split(",") : [];
        Promise.all([searchProducts(query), searchCatalog(query, storeCodes)])
            .then(([foundProducts, firstPage]) => {
                if (cancelled) return;
                setResults({
                    query,
                    storesKey,
                    products: foundProducts,
                    catalog: firstPage.entries,
                    hasMore: firstPage.hasMore,
                    loadedAll: false,
                });
                setViewPage(1);
            })
            .catch((error) => {
                if (cancelled) return;
                notify.error(error, "Търсенето не успя");
                setResults({ query, storesKey, products: [], catalog: [], hasMore: false, loadedAll: false });
                setViewPage(1);
            });
        return () => {
            cancelled = true;
        };
    }, [query, storesKey]);

    // Opens the store pages of the loaded results that have no fresh price yet.
    // One page at a time per store (the backend waits between requests anyway), the stores in parallel.
    useEffect(() => {
        if (!results) return;
        const waiting = results.catalog.filter((entry) => !entry.checked && !requestedUrls.current.has(entry.url));
        waiting.forEach((entry) => requestedUrls.current.add(entry.url));

        const search = searchNumber.current;
        async function checkOneByOne(entries: CatalogEntry[]) {
            for (const entry of entries) {
                if (searchNumber.current !== search) return;
                let checked: CatalogEntry;
                try {
                    checked = await checkCatalogEntry(entry.url);
                } catch {
                    checked = { ...entry, checked: true };   // shown as "no price"
                }
                if (searchNumber.current !== search) return;
                setResults((current) => current && {
                    ...current,
                    catalog: current.catalog.map((item) =>
                        item.url === entry.url ? { ...checked, productId: checked.productId ?? item.productId } : item,
                    ),
                });
            }
        }

        const byStore = new Map<string, CatalogEntry[]>();
        waiting.forEach((entry) => byStore.set(entry.storeCode, [...(byStore.get(entry.storeCode) ?? []), entry]));
        byStore.forEach((entries) => void checkOneByOne(entries));
    }, [results]);

    async function loadAll() {
        if (!results) return;
        const search = searchNumber.current;
        setLoadingAll(true);
        try {
            const storeCodes = results.storesKey ? results.storesKey.split(",") : [];
            const found = await searchCatalog(results.query, storeCodes, 0, LOAD_ALL_SIZE);
            if (searchNumber.current !== search) return;
            setResults((current) => {
                if (!current) return current;
                // Keep the prices we already have
                const known = new Map(current.catalog.map((entry) => [entry.url, entry]));
                return {
                    ...current,
                    catalog: found.entries.map((entry) => known.get(entry.url) ?? entry),
                    hasMore: found.hasMore,
                    loadedAll: true,
                };
            });
        } catch (error) {
            notify.error(error, "Резултатите не можаха да бъдат заредени");
        } finally {
            setLoadingAll(false);
        }
    }

    function changeFilters(next: CatalogFilters) {
        setFilters(next);
        setViewPage(1);
    }

    function goToPage(page: number) {
        setViewPage(page);
        resultsTop.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    }

    function search(text: string) {
        setInput(text);
        // Brands differ from search to search; the other filters stay
        setFilters((current) => ({ ...current, brands: [] }));
        setSearchParams(text.trim() ? { q: text.trim() } : {});
    }

    function handleSubmit(event: FormEvent) {
        event.preventDefault();
        search(input);
    }

    async function handleTrack(entry: CatalogEntry) {
        setImportingUrl(entry.url);
        try {
            const product = await importOffer(entry.url);
            notify.success("Продуктът е добавен в следените");
            navigate(`/products/${product.id}`);
        } catch (error) {
            notify.error(error, "Продуктът не можа да бъде добавен");
        } finally {
            setImportingUrl(null);
        }
    }

    async function handleOpen(group: ProductGroup) {
        if (openingKey != null) return;
        // Every store of the block is already in the same product
        if (group.productId != null && group.entries.every((entry) => entry.productId === group.productId)) {
            navigate(`/products/${group.productId}`);
            return;
        }
        setOpeningKey(group.key);
        try {
            // Links without a price are removed products, opening them would only waste time
            const urls = group.entries.filter((entry) => entry.price != null).map((entry) => entry.url);
            const product = await importGroup(urls);
            navigate(`/products/${product.id}`);
        } catch (error) {
            notify.error(error, "Продуктът не можа да бъде отворен");
        } finally {
            setOpeningKey(null);
        }
    }

    function renderGroups(list: ProductGroup[]) {
        return (
            <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
                {list.map((group) => (
                    <ProductGroupCard
                        key={group.key}
                        group={group}
                        importingUrl={importingUrl}
                        onTrack={handleTrack}
                        onOpen={handleOpen}
                        opening={openingKey === group.key}
                    />
                ))}
            </Box>
        );
    }

    return (
        <Box>
            <Typography component="h1" variant="h4" sx={{ mb: 1 }}>
                Сравни цените в онлайн магазините
            </Typography>
            <Typography color="text.secondary" sx={{ mb: 3 }}>
                Търси продукт в PIC и Ozone, добави го в следените и виж как се мени цената му.
            </Typography>

            <Box component="form" onSubmit={handleSubmit} sx={{ display: "flex", gap: 1, mb: 2 }}>
                <TextField
                    fullWidth
                    placeholder="Например: logitech g502, слушалки sony, 910-005470"
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    slotProps={{
                        input: {
                            startAdornment: (
                                <InputAdornment position="start">
                                    <SearchIcon />
                                </InputAdornment>
                            ),
                            sx: { bgcolor: "background.paper" },
                        },
                    }}
                />
                <Button type="submit" variant="contained" sx={{ px: 4 }}>
                    Търси
                </Button>
            </Box>

            {!query && (
                <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap", alignItems: "center" }}>
                    <Typography variant="body2" color="text.secondary">
                        Опитай:
                    </Typography>
                    {EXAMPLES.map((example) => (
                        <Chip key={example} label={example} onClick={() => search(example)} variant="outlined" />
                    ))}
                </Box>
            )}

            {loading && <LinearProgress sx={{ my: 3 }} />}

            {query && !loading && (
                <>
                    <Typography component="h2" variant="h6" sx={{ mt: 4, mb: 2 }}>
                        Следени продукти ({products.length})
                    </Typography>
                    {products.length === 0 ? (
                        <Typography color="text.secondary">
                            Още не следим продукти с тези думи. Избери някой от магазините по-долу.
                        </Typography>
                    ) : (
                        <Box
                            sx={{
                                display: "grid",
                                gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))",
                                gap: 2,
                            }}
                        >
                            {products.map((product) => (
                                <ProductCard key={product.id} product={product} />
                            ))}
                        </Box>
                    )}

                    <Typography ref={resultsTop} component="h2" variant="h6" sx={{ mt: 4, mb: 2, scrollMarginTop: 80 }}>
                        В магазините ({inStockGroups.length} {inStockGroups.length === 1 ? "продукт" : "продукта"})
                    </Typography>
                    <CatalogFilterBar
                        filters={filters}
                        onChange={changeFilters}
                        stores={stores}
                        brands={brandOptions(groups)}
                    />

                    {refreshing && <LinearProgress sx={{ mb: 2 }} />}
                    {!refreshing && pendingCount > 0 && (
                        <Box sx={{ mb: 2 }}>
                            <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
                                Проверяваме цените в магазините: {catalog.length - pendingCount} от {catalog.length}
                            </Typography>
                            <LinearProgress
                                variant="determinate"
                                value={((catalog.length - pendingCount) / catalog.length) * 100}
                            />
                        </Box>
                    )}

                    {catalog.length === 0 ? (
                        <Typography color="text.secondary">
                            Нищо не е намерено в каталозите на магазините.
                        </Typography>
                    ) : inStockGroups.length === 0 && pendingCount === 0 ? (
                        <Typography color="text.secondary">
                            Нищо от показаните резултати не е в наличност или не отговаря на филтрите.
                        </Typography>
                    ) : (
                        renderGroups(pageGroups)
                    )}

                    {!refreshing && (
                        <Box sx={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 2, mt: 3 }}>
                            {pageCount > 1 && (
                                <Pagination
                                    count={pageCount}
                                    page={currentPage}
                                    onChange={(_, page) => goToPage(page)}
                                    color="primary"
                                />
                            )}
                            {results?.hasMore && !results.loadedAll && (
                                <Button variant="outlined" onClick={loadAll} loading={loadingAll}>
                                    Зареди всички резултати
                                </Button>
                            )}
                            {results?.hasMore && results.loadedAll && (
                                <Typography variant="body2" color="text.secondary">
                                    Показани са първите {LOAD_ALL_SIZE} резултата. Уточни търсенето, за да видиш
                                    останалите.
                                </Typography>
                            )}
                        </Box>
                    )}

                    {soldOutGroups.length > 0 && (
                        <Accordion
                            disableGutters
                            variant="outlined"
                            sx={{ mt: 3, borderColor: "#e2e8f0", borderRadius: 1, "&::before": { display: "none" } }}
                        >
                            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                                <Typography sx={{ fontWeight: 600 }}>
                                    Изчерпани и без цена ({soldOutGroups.length})
                                </Typography>
                            </AccordionSummary>
                            <AccordionDetails sx={{ bgcolor: "background.default" }}>
                                {renderGroups(soldOutGroups)}
                            </AccordionDetails>
                        </Accordion>
                    )}
                </>
            )}
        </Box>
    );
}

export default SearchPage;
