import axiosInstance from "../api/axios";

import type { ApiResponse, CatalogEntry, CatalogPage, CatalogStatus } from "../types/api";

// The filters the server applies; an empty list means "all"
export type ServerFilters = {
    stores: string[];
    brands: string[];
    series: string[];
    memory: string[];
    colors: string[];
};

// page starts from 0; size is capped at 300 by the server. The first page also brings the filter counts.
export async function searchCatalog(query: string, filters: ServerFilters, page = 0, size = 20) {
    const list = (values: string[]) => (values.length > 0 ? values.join(",") : undefined);
    const response = await axiosInstance.get<ApiResponse<CatalogPage>>("/catalog/search", {
        params: {
            query,
            stores: list(filters.stores),
            brands: list(filters.brands),
            series: list(filters.series),
            memory: list(filters.memory),
            colors: list(filters.colors),
            page,
            size,
        },
    });
    return response.data.data;
}

// Opens the store page of a search result: can take a few seconds
export async function checkCatalogEntry(url: string) {
    const response = await axiosInstance.post<ApiResponse<CatalogEntry>>("/catalog/check", null, {
        params: { url },
    });
    return response.data.data;
}

// Admins only: the links in urls become one product with the links in targetUrls, for every later search too.
// Returns the link they were put with.
export async function joinCatalogEntries(urls: string[], targetUrls: string[]) {
    const response = await axiosInstance.post<ApiResponse<string>>("/catalog/join", { urls, targetUrls });
    return response.data.data;
}

// Admins only: takes back a join
export async function separateCatalogEntries(urls: string[]) {
    await axiosInstance.post("/catalog/separate", { urls });
}

export async function getCatalogStatus() {
    const response = await axiosInstance.get<ApiResponse<CatalogStatus>>("/catalog/status");
    return response.data.data;
}

export async function updateCatalog() {
    await axiosInstance.post("/catalog/update");
}

// Admins only: reads brand, series, memory and color of every link again, in the background
export async function refreshCatalogFilters() {
    await axiosInstance.post("/catalog/facets");
}
