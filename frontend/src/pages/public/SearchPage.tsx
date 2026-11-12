import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";

import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Box,
    Button,
    Chip,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    InputAdornment,
    LinearProgress,
    Pagination,
    Paper,
    TextField,
    Typography,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import SearchIcon from "@mui/icons-material/Search";

import { useAuth } from "../../auth/AuthContext";
import CatalogFilterBar from "../../components/catalog/CatalogFilterBar";
import ModelGroupCard from "../../components/catalog/ModelGroupCard";
import ModelOffers from "../../components/catalog/ModelOffers";
import ProductGroupCard from "../../components/catalog/ProductGroupCard";
import ProductCard from "../../components/product/ProductCard";
import {
    checkCatalogEntry,
    getCatalogStatus,
    joinCatalogEntries,
    searchCatalog,
    separateCatalogEntries,
} from "../../services/catalogService";
import type { ServerFilters } from "../../services/catalogService";
import { importGroup } from "../../services/offerService";
import { searchProducts } from "../../services/productService";
import type { CatalogEntry, CatalogFacets, ProductSummary, StoreCatalog } from "../../types/api";
import { filterAndSort, groupEntries, modelGroups, NO_FILTERS, serverFilters } from "../../utils/catalogGroups";
import type { CatalogFilters, ModelGroup, ProductGroup } from "../../utils/catalogGroups";
import { notify } from "../../utils/notify";

const EXAMPLES = ["logitech g502", "слушалки sony", "ssd 1tb", "iphone 16"];
const PRODUCTS_PER_PAGE = 20;
// The most the server gives at once; all of them are loaded
const RESULTS_SIZE = 300;
// Browsers open at most 6 connections to one server, more requests at once would only wait in the browser
const MAX_CHECKS = 6;
// Pages of one store opened at once; the backend still waits a second between requests to the same store
const MAX_CHECKS_PER_STORE = 2;

type SearchResults = {
    query: string;
    // The server filters the catalog was searched with, as JSON
    serverKey: string;
    // Brands, series, memory and colors of all results
    facets: CatalogFacets | null;
    products: ProductSummary[];
    catalog: CatalogEntry[];
    // There were more than RESULTS_SIZE links
    hasMore: boolean;
};

// The links of one search waiting for their price, most relevant first
type CheckQueue = {
    search: number;
    waiting: CatalogEntry[];
    running: number;
    runningByStore: Map<string, number>;
};

type SavedSearch = {
    results: SearchResults;
    filters: CatalogFilters;
    viewPage: number;
    scrollY: number;
    // The blocks whose stores are open
    expanded: string[];
};

// The last search, so coming back from a product page shows it as it was instead of searching again
let lastSearch: SavedSearch | null = null;

function SearchPage() {
    const navigate = useNavigate();
    const location = useLocation();
    const [searchParams, setSearchParams] = useSearchParams();
    const query = searchParams.get("q") ?? "";
    // Set on the page with the offers of a model in all its colors
    const modelKey = searchParams.get("model");

    // Only when we come back to the same search
    const [restored] = useState(() => (lastSearch?.results.query === query ? lastSearch : null));
    const [input, setInput] = useState(query);
    const [results, setResults] = useState<SearchResults | null>(restored?.results ?? null);
    const [filters, setFilters] = useState<CatalogFilters>(restored?.filters ?? NO_FILTERS);
    const [stores, setStores] = useState<StoreCatalog[]>([]);
    const { isAdmin } = useAuth();
    // The product block an admin is dragging onto another one
    const [dragged, setDragged] = useState<ProductGroup | null>(null);
    // Dropped, waiting for the admin's yes
    const [join, setJoin] = useState<{ from: ProductGroup; to: ProductGroup } | null>(null);
    const [joining, setJoining] = useState(false);
    // The product block being opened
    const [openingKey, setOpeningKey] = useState<string | null>(null);
    // From 1
    const [viewPage, setViewPage] = useState(restored?.viewPage ?? 1);
    // Keys of the blocks whose stores are open; all are closed at first
    const [expanded, setExpanded] = useState<string[]>(restored?.expanded ?? []);
    // Grows with every new search, so the price requests of an older search stop
    const searchNumber = useRef(0);
    // Links whose price was already asked for in this search
    const requestedUrls = useRef(new Set<string>());
    const checks = useRef<CheckQueue | null>(null);
    const resultsTop = useRef<HTMLHeadingElement>(null);
    // "query|filters" of the restored search, which then needs no new request
    const restoredKey = useRef(restored ? `${restored.results.query}|${restored.results.serverKey}` : null);
    const scrollY = useRef(restored?.scrollY ?? 0);
    const shownModelKey = useRef(modelKey);

    const serverKey = JSON.stringify(serverFilters(filters));
    // Still loading while the shown results belong to an older query
    const loading = query !== "" && results?.query !== query;
    // A new store filter: the old results stay on screen until the new ones come
    const refreshing = !loading && results != null && results.serverKey !== serverKey;
    const products = results?.products ?? [];
    // Stores whose catalog we have, i.e. the ones a search goes through
    const searchedStores = stores.filter((store) => store.links > 0).length;
    const catalog = results?.catalog ?? [];

    const pendingCount = catalog.filter((entry) => !entry.checked).length;
    // Results join a product block once their page was opened and we know their EAN or model
    const groups = groupEntries(catalog.filter((entry) => entry.checked));
    const visibleGroups = filterAndSort(groups, filters);
    // The searched model with all its colors together, shown before everything else
    const models = modelGroups(visibleGroups);
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

    // Back from a product page: put the page where it was
    useEffect(() => {
        if (restored) window.scrollTo(0, restored.scrollY);
    }, [restored]);

    // The model page starts at its top; back in the results the page is where it was left
    useEffect(() => {
        if (shownModelKey.current === modelKey) return;
        shownModelKey.current = modelKey;
        window.scrollTo(0, modelKey ? 0 : scrollY.current);
    }, [modelKey]);

    useEffect(() => {
        if (results) {
            lastSearch = { results, filters, viewPage, scrollY: scrollY.current, expanded };
        }
    }, [results, filters, viewPage, expanded]);

    useEffect(() => {
        if (!query) return;
        const key = `${query}|${serverKey}`;
        if (restoredKey.current === key) return;
        restoredKey.current = null;
        searchNumber.current += 1;
        requestedUrls.current = new Set();
        let cancelled = false;
        const chosen: ServerFilters = JSON.parse(serverKey);
        Promise.all([searchProducts(query), searchCatalog(query, chosen, 0, RESULTS_SIZE)])
            .then(([foundProducts, found]) => {
                if (cancelled) return;
                setResults({
                    query,
                    serverKey,
                    facets: found.facets,
                    products: foundProducts,
                    catalog: found.entries,
                    hasMore: found.hasMore,
                });
                setViewPage(1);
            })
            .catch((error) => {
                if (cancelled) return;
                notify.error(error, "Търсенето не успя");
                setResults({ query, serverKey, facets: null, products: [], catalog: [], hasMore: false });
                setViewPage(1);
            });
        return () => {
            cancelled = true;
        };
    }, [query, serverKey]);

    // Opens the store pages of the loaded results that have no fresh price yet, the most relevant first.
    // A few stores at once, so a store with slow pages and many links doesn't hold the others back.
    useEffect(() => {
        if (!results) return;
        const waiting = results.catalog.filter((entry) => !entry.checked && !requestedUrls.current.has(entry.url));
        if (waiting.length === 0) return;
        waiting.forEach((entry) => requestedUrls.current.add(entry.url));

        if (checks.current?.search !== searchNumber.current) {
            checks.current = { search: searchNumber.current, waiting: [], running: 0, runningByStore: new Map() };
        }
        const queue = checks.current;
        queue.waiting.push(...waiting);
        const runningIn = (store: string) => queue.runningByStore.get(store) ?? 0;

        async function check(entry: CatalogEntry) {
            let checked: CatalogEntry;
            try {
                checked = await checkCatalogEntry(entry.url);
            } catch {
                checked = { ...entry, checked: true };   // shown as "no price"
            }
            if (searchNumber.current !== queue.search) return;
            setResults((current) => current && {
                ...current,
                // A removed product is left out, the next searches don't show it either
                catalog: current.catalog.flatMap((item) =>
                    item.url !== entry.url ? [item]
                        : checked.gone ? []
                        : [{ ...checked, productId: checked.productId ?? item.productId, accessory: item.accessory }],
                ),
            });
        }

        function startNext() {
            while (queue.running < MAX_CHECKS && searchNumber.current === queue.search) {
                // The most relevant link whose store isn't busy
                const index = queue.waiting.findIndex((entry) => runningIn(entry.storeCode) < MAX_CHECKS_PER_STORE);
                if (index < 0) return;
                const [entry] = queue.waiting.splice(index, 1);
                queue.running += 1;
                queue.runningByStore.set(entry.storeCode, runningIn(entry.storeCode) + 1);
                void check(entry).finally(() => {
                    queue.running -= 1;
                    queue.runningByStore.set(entry.storeCode, runningIn(entry.storeCode) - 1);
                    startNext();
                });
            }
        }

        startNext();
    }, [results]);

    // Saved on every click, because a click may open a product page
    function rememberScroll() {
        // Not on the model page: the place to come back to is in the results
        if (modelKey) return;
        scrollY.current = window.scrollY;
        if (lastSearch) lastSearch.scrollY = window.scrollY;
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
        // Brands, series, memory and colors differ from search to search; the other filters stay
        setFilters((current) => ({ ...current, brands: [], series: [], memory: [], colors: [] }));
        setExpanded([]);
        setSearchParams(text.trim() ? { q: text.trim() } : {});
    }

    function toggleStores(key: string) {
        setExpanded((open) => (open.includes(key) ? open.filter((other) => other !== key) : [...open, key]));
    }

    function openModel(model: ModelGroup) {
        setSearchParams({ q: query, model: model.key }, { state: { fromSearch: true } });
    }

    function closeModel() {
        // Opened from the results: going back keeps the back button of the browser right
        if (location.state?.fromSearch) {
            navigate(-1);
        } else {
            setSearchParams({ q: query });
        }
    }

    function handleSubmit(event: FormEvent) {
        event.preventDefault();
        search(input);
    }

    // Changes some of the shown links, e.g. after an admin joined them to another product
    function changeEntries(urls: string[], change: Partial<CatalogEntry>) {
        setResults((current) => current && { ...current, catalog: withChange(current.catalog, urls, change) });
    }

    // The block's links become one product, unless they already are
    async function toProduct(group: ProductGroup) {
        if (group.productId != null && group.entries.every((entry) => entry.productId === group.productId)) {
            return group.productId;
        }
        // Links without a price are removed products, opening them would only waste time
        const urls = group.entries.filter((entry) => entry.price != null).map((entry) => entry.url);
        const product = await importGroup(urls);
        const opened = { productId: product.id };
        // Also the saved search directly: a product page may open before the state is saved
        if (lastSearch) {
            const saved = lastSearch.results;
            lastSearch = { ...lastSearch, results: { ...saved, catalog: withChange(saved.catalog, urls, opened) } };
        }
        changeEntries(urls, opened);
        return product.id;
    }

    function handleDrop(target: ProductGroup) {
        const from = dragged;
        setDragged(null);
        if (!from || from.key === target.key) return;
        setJoin({ from, to: target });
    }

    async function confirmJoin() {
        if (!join) return;
        setJoining(true);
        try {
            const urls = join.from.entries.map((entry) => entry.url);
            const joinedToUrl = await joinCatalogEntries(urls, join.to.entries.map((entry) => entry.url));
            changeEntries(urls, { joinedToUrl });
            // Two tracked products are one product now, the one they were put with
            if (join.to.productId != null) {
                const tracked = join.from.entries.filter((entry) => entry.productId != null).map((entry) => entry.url);
                changeEntries(tracked, { productId: join.to.productId });
            }
            notify.success("Продуктите са събрани в един");
            setJoin(null);
        } catch (error) {
            notify.error(error, "Продуктите не можаха да бъдат събрани");
        } finally {
            setJoining(false);
        }
    }

    // A store can have several links in the block; all of the moved ones go back
    async function handleSeparate(group: ProductGroup, entry: CatalogEntry) {
        const urls = group.entries
            .filter((other) => other.storeCode === entry.storeCode && other.joinedToUrl)
            .map((other) => other.url);
        try {
            await separateCatalogEntries(urls);
            changeEntries(urls, { joinedToUrl: null });
            notify.success(`${entry.storeName} е отделен`);
        } catch (error) {
            notify.error(error, "Магазинът не можа да бъде отделен");
        }
    }

    async function handleOpen(group: ProductGroup) {
        if (openingKey != null) return;
        setOpeningKey(group.key);
        try {
            navigate(`/products/${await toProduct(group)}`);
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
                        onOpen={handleOpen}
                        opening={openingKey === group.key}
                        toProduct={toProduct}
                        expanded={expanded.includes(group.key)}
                        onToggle={(toggled) => toggleStores(toggled.key)}
                        joining={
                            isAdmin
                                ? {
                                      canDrop: dragged != null && dragged.key !== group.key,
                                      picked: dragged?.key === group.key,
                                      // A second tap on the same product lets it go
                                      onPick: (picked) =>
                                          setDragged((current) => (current?.key === picked.key ? null : picked)),
                                      // A moment later: Chrome stops a drag if the page changes while it starts
                                      onDragStart: (from) => setTimeout(() => setDragged(from), 0),
                                      onDragEnd: () => setDragged(null),
                                      onDrop: handleDrop,
                                      onSeparate: handleSeparate,
                                  }
                                : null
                        }
                    />
                ))}
            </Box>
        );
    }

    if (modelKey) {
        return (
            <ModelOffers
                // A new model starts with all its colors again
                key={modelKey}
                model={models.find((model) => model.key === modelKey) ?? null}
                checking={loading || refreshing || pendingCount > 0}
                onBack={closeModel}
            />
        );
    }

    return (
        <Box onClickCapture={rememberScroll}>
            <Typography component="h1" variant="h4" sx={{ mb: 1 }}>
                Сравни цените в онлайн магазините
            </Typography>
            <Typography color="text.secondary" sx={{ mb: 3 }}>
                Търси продукт в {searchedStores > 0 ? `${searchedStores} онлайн магазина` : "онлайн магазините"}, сравни
                цените и добави в любими, за да виждаш как се мени цената.
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
                    {models.length > 0 && (
                        <Box sx={{ display: "flex", flexDirection: "column", gap: 2, mt: 3 }}>
                            {models.map((model) => (
                                <ModelGroupCard
                                    key={model.key}
                                    model={model}
                                    onOpen={openModel}
                                    expanded={expanded.includes(`model:${model.key}`)}
                                    onToggle={(toggled) => toggleStores(`model:${toggled.key}`)}
                                />
                            ))}
                        </Box>
                    )}

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
                        facets={results?.facets ?? null}
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
                            {results?.hasMore && (
                                <Typography variant="body2" color="text.secondary">
                                    Показани са първите {RESULTS_SIZE}
                                    {results.facets ? ` от ${results.facets.total.toLocaleString("bg-BG")}` : ""}{" "}
                                    резултата. Уточни търсенето, за да видиш останалите.
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

            {dragged && (
                <Paper
                    elevation={6}
                    sx={{
                        position: "fixed",
                        bottom: 16,
                        left: "50%",
                        transform: "translateX(-50%)",
                        zIndex: (theme) => theme.zIndex.snackbar,
                        width: "max-content",
                        maxWidth: "calc(100vw - 32px)",
                        px: 2,
                        py: 1,
                        display: "flex",
                        alignItems: "center",
                        gap: 1,
                    }}
                >
                    <Typography variant="body2" noWrap sx={{ minWidth: 0 }}>
                        Избери с кой продукт да се събере <strong>{dragged.name}</strong>
                    </Typography>
                    <Button size="small" onClick={() => setDragged(null)} sx={{ flexShrink: 0 }}>
                        Отказ
                    </Button>
                </Paper>
            )}

            <Dialog open={join != null} onClose={joining ? undefined : () => setJoin(null)} fullWidth maxWidth="sm">
                <DialogTitle>Един и същ продукт ли са?</DialogTitle>
                {join && (
                    <DialogContent>
                        <DialogContentText>
                            <strong>{join.from.name}</strong> ({storeNames(join.from)}) ще се показва заедно с{" "}
                            <strong>{join.to.name}</strong> ({storeNames(join.to)}), за всички посетители и при всяко
                            търсене.
                        </DialogContentText>
                        {join.from.productId != null &&
                            join.to.productId != null &&
                            join.from.productId !== join.to.productId && (
                                <DialogContentText sx={{ mt: 2 }} color="warning.main">
                                    И двата продукта вече се следят. Офертите, историята на цените и любимите им ще
                                    се съберат в един продукт и това не може да се върне.
                                </DialogContentText>
                            )}
                    </DialogContent>
                )}
                <DialogActions>
                    <Button onClick={() => setJoin(null)} disabled={joining}>
                        Отказ
                    </Button>
                    <Button variant="contained" onClick={confirmJoin} loading={joining}>
                        Събери ги
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
}

function withChange(list: CatalogEntry[], urls: string[], change: Partial<CatalogEntry>) {
    const changed = new Set(urls);
    return list.map((entry) => (changed.has(entry.url) ? { ...entry, ...change } : entry));
}

function storeNames(group: ProductGroup) {
    return [...new Set(group.entries.map((entry) => entry.storeName))].join(", ");
}

export default SearchPage;
