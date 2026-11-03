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

export type CatalogFilters = {
    // Store codes; empty = all stores
    stores: string[];
    minPrice: number | null;
    maxPrice: number | null;
    // Brand keys, see brandKey()
    brands: string[];
    sort: SortOrder;
};

export const NO_FILTERS: CatalogFilters = { stores: [], minPrice: null, maxPrice: null, brands: [], sort: "relevance" };

export function isAvailable(entry: CatalogEntry) {
    return entry.price != null && entry.inStock !== false;
}

// "SAMSUNG", "Samsung" and "samsung " are one brand
export function brandKey(brand: string) {
    return brand.toLowerCase().replace(/[^\p{L}\p{N}]/gu, "");
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
        const keys = [
            entry.productId != null ? `product:${entry.productId}` : null,
            entry.ean ? `ean:${entry.ean}` : null,
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

// The brands found in the groups, most common first: [key, name as written, count]
export function brandOptions(groups: ProductGroup[]): [string, string, number][] {
    const byKey = new Map<string, [string, string, number]>();
    for (const group of groups) {
        if (!group.brand) continue;
        const key = brandKey(group.brand);
        const known = byKey.get(key);
        if (known) {
            known[2] += 1;
            // Prefer "Samsung" over "SAMSUNG"
            if (known[1] === known[1].toUpperCase()) known[1] = group.brand;
        } else {
            byKey.set(key, [key, group.brand, 1]);
        }
    }
    return [...byKey.values()].sort((a, b) => b[2] - a[2] || a[1].localeCompare(b[1]));
}

export function filterAndSort(groups: ProductGroup[], filters: CatalogFilters): ProductGroup[] {
    const filtered = groups.filter((group) => {
        if (filters.brands.length > 0 && !(group.brand && filters.brands.includes(brandKey(group.brand)))) {
            return false;
        }
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
