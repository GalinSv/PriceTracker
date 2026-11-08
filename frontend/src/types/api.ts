export type ApiResponse<T> = {
    success: boolean;
    data: T;
    message: string | null;
};

export type StoreStatus = "ACTIVE" | "DISABLED" | "BLOCKED";

export type Store = {
    id: number;
    code: string;
    name: string;
    baseUrl: string;
    logoUrl: string | null;
    status: StoreStatus;
};

export type ProductSummary = {
    id: number;
    name: string;
    brand: string | null;
    imageUrl: string | null;
    lowestPrice: number | null;
    offerCount: number;
};

export type Offer = {
    id: number;
    storeCode: string;
    storeName: string;
    url: string;
    title: string;
    currentPrice: number | null;
    currency: string;
    inStock: boolean;
    lastCheckedAt: string | null;
};

export type ProductDetails = {
    id: number;
    name: string;
    brand: string | null;
    modelCode: string | null;
    ean: string | null;
    imageUrl: string | null;
    categoryName: string | null;
    lowestPrice: number | null;
    offers: Offer[];
};

export type PriceSnapshot = {
    price: number;
    currency: string;
    inStock: boolean;
    capturedAt: string;
};

export type CatalogEntry = {
    storeCode: string;
    storeName: string;
    url: string;
    name: string;
    productId: number | null;
    price: number | null;
    currency: string | null;
    inStock: boolean | null;
    // false = no fresh price yet, ask for it with checkCatalogEntry
    checked: boolean;
    // Known after the page was opened once; results with a shared EAN or the same model key are the same product
    eans: string[];
    brand: string | null;
    modelKey: string | null;
    imageUrl: string | null;
};

export type FacetValue = {
    value: string;
    // How many links of the search have it
    count: number;
};

export type CatalogFacets = {
    brands: FacetValue[];
    series: FacetValue[];
    memory: FacetValue[];
    colors: FacetValue[];
};

export type CatalogPage = {
    entries: CatalogEntry[];
    hasMore: boolean;
    // Only with the first page
    facets: CatalogFacets | null;
};

export type StoreCatalog = {
    storeCode: string;
    storeName: string;
    links: number;
    updatedAt: string | null;
};

export type CatalogStatus = {
    updating: boolean;
    stores: StoreCatalog[];
};

export type RefreshResult = {
    checked: number;
    changed: number;
    notFound: number;
    failed: number;
};

export type Favorite = {
    productId: number;
    name: string;
    brand: string | null;
    imageUrl: string | null;
    // null when no store has it in stock
    lowestPrice: number | null;
    priceWhenAdded: number | null;
    // The lowest price at the last visit of the favorites page
    seenPrice: number | null;
    targetPrice: number | null;
    targetReached: boolean;
    inStockCount: number;
    offerCount: number;
    // Stores added after the last visit
    newStores: string[];
    news: boolean;
    addedAt: string;
};

export type FavoritesSummary = {
    productIds: number[];
    newsCount: number;
};
