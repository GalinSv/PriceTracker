import type { ServerFilters } from "../services/catalogService";
import type { CatalogEntry } from "../types/api";

// One product with its offers from the search results of different stores
export type ProductGroup = {
    key: string;
    // In stock first, cheapest first
    entries: CatalogEntry[];
    name: string;
    brand: string | null;
    imageUrl: string | null;
    productId: number | null;
    // The lowest price among the stores that have it in stock
    lowestPrice: number | null;
    inStockCount: number;
    // Position of the group's best search hit, for sorting by relevance
    rank: number;
};

export type SortOrder = "relevance" | "priceAsc" | "priceDesc" | "stores";

// The server applies stores, brands, series, memory and colors (empty = all); price and sort apply here
export type CatalogFilters = ServerFilters & {
    minPrice: number | null;
    maxPrice: number | null;
    sort: SortOrder;
};

export const NO_FILTERS: CatalogFilters = {
    stores: [],
    brands: [],
    series: [],
    memory: [],
    colors: [],
    minPrice: null,
    maxPrice: null,
    sort: "relevance",
};

export function serverFilters(filters: CatalogFilters): ServerFilters {
    const { stores, brands, series, memory, colors } = filters;
    return { stores, brands, series, memory, colors };
}

export function isAvailable(entry: CatalogEntry) {
    return entry.price != null && entry.inStock !== false;
}

// Results are the same product if they share a tracked product, an EAN or brand + model code.
// One shared key is enough, so a store with only an EAN and a store with only a model can still meet
// through a third store that has both.
export function groupEntries(entries: CatalogEntry[]): ProductGroup[] {
    const parent = entries.map((_, index) => index);
    const find = (index: number): number => {
        while (parent[index] !== index) {
            parent[index] = parent[parent[index]];
            index = parent[index];
        }
        return index;
    };

    const firstWithKey = new Map<string, number>();
    entries.forEach((entry, index) => {
        // A store can list several barcodes for one product; any shared one is enough
        const keys = [
            entry.productId != null ? `product:${entry.productId}` : null,
            ...entry.eans.map((ean) => `ean:${ean}`),
            entry.modelKey ? `model:${entry.modelKey}` : null,
        ];
        for (const key of keys) {
            if (key == null) continue;
            const other = firstWithKey.get(key);
            if (other == null) {
                firstWithKey.set(key, index);
            } else {
                parent[find(index)] = find(other);
            }
        }
    });

    const indexesByRoot = new Map<number, number[]>();
    entries.forEach((_, index) => {
        const root = find(index);
        indexesByRoot.set(root, [...(indexesByRoot.get(root) ?? []), index]);
    });
    return [...indexesByRoot.values()].map((indexes) => toGroup(indexes.map((index) => entries[index]), indexes[0]));
}

function toGroup(entries: CatalogEntry[], rank: number): ProductGroup {
    const inStock = entries.filter(isAvailable).sort((a, b) => (a.price ?? 0) - (b.price ?? 0));
    const rest = entries.filter((entry) => !isAvailable(entry));
    // Some stores write names in capitals only, e.g. "SAMSUNG GALAXY S26 ULTRA"
    const named = entries.find((entry) => entry.name !== entry.name.toUpperCase()) ?? entries[0];
    return {
        key: entries[0].url,
        entries: [...inStock, ...rest],
        name: named.name,
        brand: entries.find((entry) => entry.brand)?.brand ?? null,
        imageUrl: entries.find((entry) => entry.imageUrl)?.imageUrl ?? null,
        productId: entries.find((entry) => entry.productId != null)?.productId ?? null,
        lowestPrice: inStock[0]?.price ?? null,
        inStockCount: inStock.length,
        rank,
    };
}

export function filterAndSort(groups: ProductGroup[], filters: CatalogFilters): ProductGroup[] {
    const filtered = groups.filter((group) => {
        const price = groupPrice(group);
        if (filters.minPrice != null && (price == null || price < filters.minPrice)) return false;
        if (filters.maxPrice != null && (price == null || price > filters.maxPrice)) return false;
        return true;
    });

    // Products without a price go last either way
    const priceOr = (group: ProductGroup, missing: number) => groupPrice(group) ?? missing;
    switch (filters.sort) {
        case "priceAsc":
            return filtered.sort((a, b) => priceOr(a, Infinity) - priceOr(b, Infinity) || a.rank - b.rank);
        case "priceDesc":
            return filtered.sort((a, b) => priceOr(b, -Infinity) - priceOr(a, -Infinity) || a.rank - b.rank);
        case "stores":
            return filtered.sort((a, b) => b.entries.length - a.entries.length || a.rank - b.rank);
        default:
            return filtered.sort((a, b) => a.rank - b.rank);
    }
}

// The lowest price in stock, or for sold out products the lowest price at all
function groupPrice(group: ProductGroup) {
    if (group.lowestPrice != null) return group.lowestPrice;
    const prices = group.entries.map((entry) => entry.price).filter((price): price is number => price != null);
    return prices.length > 0 ? Math.min(...prices) : null;
}
