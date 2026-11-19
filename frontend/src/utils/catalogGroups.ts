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
            // Joined by an admin: a moved link has the key of the link it was put with
            `link:${entry.url}`,
            entry.joinedToUrl ? `link:${entry.joinedToUrl}` : null,
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

    const groupIndexes = () => {
        const indexesByRoot = new Map<number, number[]>();
        entries.forEach((_, index) => {
            const root = find(index);
            indexesByRoot.set(root, [...(indexesByRoot.get(root) ?? []), index]);
        });
        return [...indexesByRoot.values()];
    };

    const entriesOf = (indexes: number[]) => indexes.map((index) => entries[index]);
    const sameKey = groupIndexes();
    for (const [from, to] of regionVersions(sameKey.map(entriesOf))) {
        parent[find(sameKey[from][0])] = find(sameKey[to][0]);
    }
    const exact = groupIndexes();
    for (const [short, long] of cutModelCodes(exact.map(entriesOf))) {
        parent[find(exact[short][0])] = find(exact[long][0]);
    }
    return groupIndexes().map((indexes) => toGroup(entriesOf(indexes), indexes[0]));
}

// Some makers end their codes with the region a device was made for. The part before it names the model, the
// color and the memory, so codes that differ only in the region are one product with several barcodes:
// - Samsung: "SM-S948BLBDEUB" and "SM-S948BLBDEUE" are the same Sky Blue 256 GB phone
// - Apple: "MFYM4HX/A" and "MFYM4ZD/A" are the same silver 256 GB iPhone
// Other makers have no such part: the end of JBL's "JBLFLIP7BLK" and "JBLFLIP7BLU" is the color.
const REGION_CODES = [
    /^(samsung:SM[A-Z]\d{3}[A-Z][A-Z]{2}[A-Z0-9])[A-Z]{2,3}$/,
    /^(apple:M[A-Z0-9]{4})[A-Z]{1,2}A$/,
];
// BestPC gives its own number as the code of some offers, but their names have Samsung's: "... SM-S948BZKHEUB, 16GB"
const SAMSUNG_CODE_IN_NAME = /SM-?([A-Z]\d{3}[A-Z][A-Z]{2}[A-Z0-9])[A-Z]{2,3}(?![A-Z0-9])/;

// The entry's device without the region, or null when its maker's codes have none
function deviceOf(entry: CatalogEntry) {
    for (const code of REGION_CODES) {
        const device = code.exec(entry.modelKey ?? "")?.[1];
        if (device) return device;
    }
    if (entry.brand?.toLowerCase() !== "samsung") return null;
    const named = SAMSUNG_CODE_IN_NAME.exec(entry.name.toUpperCase());
    return named ? `samsung:SM${named[1]}` : null;
}

// Returns [group, group to put it with] for the groups that differ only in the region of their code.
// Two tracked products stay apart: each has its own price history.
function regionVersions(groups: CatalogEntry[][]): [number, number][] {
    const productOf = (group: CatalogEntry[]) => group.find((entry) => entry.productId != null)?.productId ?? null;
    const byDevice = new Map<string, number[]>();
    groups.forEach((group, index) => {
        const devices = new Set(group.flatMap((entry) => deviceOf(entry) ?? []));
        for (const device of devices) byDevice.set(device, [...(byDevice.get(device) ?? []), index]);
    });

    const pairs: [number, number][] = [];
    for (const indexes of byDevice.values()) {
        // A tracked one first, so the others join it
        const target = indexes.find((index) => productOf(groups[index]) != null) ?? indexes[0];
        const product = productOf(groups[target]);
        for (const index of indexes) {
            const other = productOf(groups[index]);
            if (index !== target && (other == null || other === product)) pairs.push([index, target]);
        }
    }
    return pairs;
}

// Model codes shorter than this are names like "S24" or "G502", and "G502" also starts "G502X"
const MIN_CUT_CODE_LENGTH = 6;

// Some stores cut the end of the model code: JAR's "WF1000XM5BC" is Ozone's "WF1000XM5BCE7".
// Returns [group with the cut code, group with the whole code] for the groups that are surely one product:
// - the cut code starts the code of exactly one other group (a code without the color starts every color's)
// - both names say the same color, and no different memory size
// - no store is in both groups, as a store doesn't list one product twice
// - they don't both have barcodes (the same barcode would have joined them already) or tracked products
function cutModelCodes(groups: CatalogEntry[][]): [number, number][] {
    const known = (values: (string | null)[]) => [...new Set(values.filter((value): value is string => !!value))];
    const share = (a: string[], b: string[]) => a.some((value) => b.includes(value));
    const facts = groups.map((group) => ({
        models: known(group.map((entry) => entry.modelKey)),
        stores: known(group.map((entry) => entry.storeCode)),
        colors: known(group.map((entry) => entry.color)),
        memory: known(group.map((entry) => entry.memory)),
        hasEan: group.some((entry) => entry.eans.length > 0),
        tracked: group.some((entry) => entry.productId != null),
    }));
    // The key is "brand:code", so a key that starts another one has the same brand
    const isCutOf = (cut: string, whole: string) =>
        whole.length > cut.length && whole.startsWith(cut) && cut.length - cut.indexOf(":") - 1 >= MIN_CUT_CODE_LENGTH;

    const pairs: [number, number][] = [];
    facts.forEach((cut, cutIndex) => {
        const wholes = facts.flatMap((other, index) =>
            index !== cutIndex && cut.models.some((model) => other.models.some((whole) => isCutOf(model, whole)))
                ? [index]
                : [],
        );
        if (wholes.length !== 1) return;
        const whole = facts[wholes[0]];
        const sameProduct =
            share(cut.colors, whole.colors) &&
            (cut.memory.length === 0 || whole.memory.length === 0 || share(cut.memory, whole.memory)) &&
            !share(cut.stores, whole.stores) &&
            !(cut.hasEan && whole.hasEan) &&
            !(cut.tracked && whole.tracked);
        if (sameProduct) pairs.push([cutIndex, wholes[0]]);
    });
    return pairs;
}

function toGroup(entries: CatalogEntry[], rank: number): ProductGroup {
    const inStock = entries.filter(isAvailable).sort((a, b) => (a.price ?? 0) - (b.price ?? 0));
    const rest = entries.filter((entry) => !isAvailable(entry));
    // A product keeps its name and picture when an admin moves another one's links into it
    const moved = entries.filter((entry) => entry.joinedToUrl);
    const own = moved.length < entries.length ? entries.filter((entry) => !entry.joinedToUrl) : entries;
    // Some stores write names in capitals only, e.g. "SAMSUNG GALAXY S26 ULTRA"
    const named = own.find((entry) => entry.name !== entry.name.toUpperCase()) ?? own[0];
    return {
        key: entries[0].url,
        entries: [...inStock, ...rest],
        name: named.name,
        brand: entries.find((entry) => entry.brand)?.brand ?? null,
        imageUrl: (own.find((entry) => entry.imageUrl) ?? entries.find((entry) => entry.imageUrl))?.imageUrl ?? null,
        productId: entries.find((entry) => entry.productId != null)?.productId ?? null,
        lowestPrice: inStock[0]?.price ?? null,
        inStockCount: inStock.length,
        rank,
    };
}

// One model in all its colors, e.g. every "Samsung Galaxy S26 Ultra 256 GB"
export type ModelGroup = {
    // For the address of its page
    key: string;
    // "Samsung Galaxy S26 Ultra 256 GB"
    name: string;
    brand: string | null;
    imageUrl: string | null;
    // One per color, the best search hit first
    groups: ProductGroup[];
    lowestPrice: number | null;
};

// An offer of a model: the best link of one store for one color
export type ModelOffer = {
    entry: CatalogEntry;
    color: string | null;
};

// The value most of the group's links have; stores name the same product differently
function mostCommon(values: (string | null)[]) {
    const counts = new Map<string, number>();
    for (const value of values) {
        if (value) counts.set(value, (counts.get(value) ?? 0) + 1);
    }
    return [...counts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] ?? null;
}

// "256 GB" -> 256, "1 TB" -> 1024
function gigabytes(memory: string) {
    const [number, unit] = memory.split(" ");
    return Number(number) * (unit === "TB" ? 1024 : 1);
}

export function groupColor(group: ProductGroup) {
    return mostCommon(group.entries.map((entry) => entry.color));
}

// The biggest size a store names: one that writes only "12GB RAM" must not hide the "256 GB" of the others
function groupMemory(group: ProductGroup) {
    const sizes = group.entries.flatMap((entry) => (entry.memory ? [entry.memory] : []));
    return sizes.sort((a, b) => gigabytes(b) - gigabytes(a))[0] ?? null;
}

// A group left with only the RAM as its size ("12 GB" next to "256 GB" and "1 TB") is not a model of its own
const MAX_RAM_GB = 32;
const RAM_TO_STORAGE = 8;
// A product of the model costs at most this many times more or less than the model's best search hit
const MAX_PRICE_RATIO = 2;

// The most models on top of a search, and the most blocks they make together (a model has one per memory size)
const MAX_MODELS = 3;
const MAX_MODEL_BLOCKS = 9;

type SizedProduct = { group: ProductGroup; memory: string | null };

// The models of the best search hits, each with its colors put together: one ModelGroup per memory size.
// "galaxy s26" gives the S26, the S26 Plus and the S26 Ultra; "galaxy s26 ultra" only the last one.
// A name without a number is no model: "JBL Tune" is a whole line of different headphones.
export function modelGroups(groups: ProductGroup[]): ModelGroup[] {
    let products: SizedProduct[] = groups
        .filter((group) => group.entries.filter((entry) => entry.accessory).length * 2 < group.entries.length)
        .map((group) => ({ group, memory: groupMemory(group) }))
        .sort((a, b) => a.group.rank - b.group.rank);

    const models: { words: number; sizes: ModelGroup[] }[] = [];
    const tried = new Set<string>();
    for (const { group } of [...products]) {
        const series = mostCommon(group.entries.map((entry) => entry.series));
        if (series == null || !/\d/.test(series) || tried.has(series)) continue;
        tried.add(series);

        // Stores add to the name: "Samsung Galaxy S26 Ultra S948". Extra words with a digit are still the model,
        // extra words without one are another model: "iPhone 17 Pro" is not an "iPhone 17".
        const modelWords = series.toLowerCase().split(" ");
        const isModel = (name: string | null) => {
            const words = (name ?? "").toLowerCase().split(" ");
            return (
                modelWords.every((word, index) => words[index] === word) &&
                words.slice(modelWords.length).every((word) => /\d/.test(word))
            );
        };
        // One store that names the model is enough: others may call the same phone by its code
        const sameModel = products.filter((product) => product.group.entries.some((entry) => isModel(entry.series)));
        const sizes = sizeBlocks(series, sameModel);
        if (sizes.length === 0) continue;

        models.push({ words: modelWords.length, sizes });
        // A product is in one model only
        const taken = new Set(sizes.flatMap((size) => size.groups));
        products = products.filter((product) => !taken.has(product.group));
        if (models.length === MAX_MODELS) break;
    }
    // The plainest name first: "Galaxy S26" before "Galaxy S26 FE" and "Galaxy S26 Ultra"
    return models
        .sort((a, b) => a.words - b.words)
        .flatMap((model) => model.sizes)
        .slice(0, MAX_MODEL_BLOCKS);
}

// The products of one model by memory size, smallest first.
// - Only sizes with at least two products in stock, as one product alone already has its own block.
// - When the model comes in several sizes, products whose stores don't say the size are left out.
function sizeBlocks(series: string, sameModel: SizedProduct[]): ModelGroup[] {
    const letters = (text: string) => text.toLowerCase().replace(/[^\p{L}\p{N}]/gu, "");
    const biggest = Math.max(0, ...sameModel.map((product) => (product.memory ? gigabytes(product.memory) : 0)));
    const isRam = (memory: string) =>
        gigabytes(memory) <= MAX_RAM_GB && gigabytes(memory) * RAM_TO_STORAGE <= biggest;

    const bySize = new Map<string, ProductGroup[]>();
    for (const product of sameModel) {
        if (biggest > 0 && (product.memory == null || isRam(product.memory))) continue;
        const size = product.memory ?? "";
        bySize.set(size, [...(bySize.get(size) ?? []), product.group]);
    }

    // The colors of a model cost about the same. A case that a store names like the model costs a fraction
    // of the best hit's price, so it is left out.
    const nearBestHit = (colors: ProductGroup[]) => {
        const price = colors.find((group) => group.lowestPrice != null)?.lowestPrice;
        return colors.filter(
            (group) =>
                price == null ||
                group.lowestPrice == null ||
                (group.lowestPrice * MAX_PRICE_RATIO >= price && group.lowestPrice <= price * MAX_PRICE_RATIO),
        );
    };

    return [...bySize.entries()]
        .map(([size, colors]) => [size, nearBestHit(colors)] as const)
        .filter(([, colors]) => colors.filter((group) => group.inStockCount > 0).length >= 2)
        // And from at least two stores: two links of one store are no comparison
        .filter(([, colors]) => {
            const stores = colors.flatMap((group) => group.entries.filter(isAvailable).map((entry) => entry.storeCode));
            return new Set(stores).size >= 2;
        })
        .sort(([a], [b]) => (a ? gigabytes(a) : 0) - (b ? gigabytes(b) : 0))
        .map(([size, colors]) => {
            const prices = colors.flatMap((group) => (group.lowestPrice != null ? [group.lowestPrice] : []));
            return {
                key: `${letters(series)}|${size}`,
                name: size ? `${series} ${size}` : series,
                brand: colors.find((group) => group.brand)?.brand ?? null,
                imageUrl: colors.find((group) => group.imageUrl)?.imageUrl ?? null,
                groups: colors,
                lowestPrice: prices.length > 0 ? Math.min(...prices) : null,
            };
        });
}

// Every store's best link for every color of the model: in stock and cheapest first
export function modelOffers(model: ModelGroup): ModelOffer[] {
    const offers = model.groups.flatMap((group) => {
        const color = groupColor(group);
        // The group's links are in stock and cheapest first, so a store's first link is its best offer
        const stores = new Set<string>();
        return group.entries.flatMap((entry) => {
            if (stores.has(entry.storeCode)) return [];
            stores.add(entry.storeCode);
            return [{ entry, color }];
        });
    });
    const available = offers.filter((offer) => isAvailable(offer.entry));
    const rest = offers.filter((offer) => !isAvailable(offer.entry));
    return [...available.sort((a, b) => (a.entry.price ?? 0) - (b.entry.price ?? 0)), ...rest];
}

// The colors the offers come in, the cheapest first
export function offerColors(offers: ModelOffer[]) {
    return [...new Set(offers.flatMap((offer) => (offer.color ? [offer.color] : [])))];
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
