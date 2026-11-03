import axiosInstance from "../api/axios";

import type { ApiResponse, CatalogEntry, CatalogPage, CatalogStatus } from "../types/api";

// stores: store codes to search in; empty = all stores. page starts from 0.
export async function searchCatalog(query: string, stores: string[] = [], page = 0) {
    const response = await axiosInstance.get<ApiResponse<CatalogPage>>("/catalog/search", {
        params: { query, stores: stores.length > 0 ? stores.join(",") : undefined, page },
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

export async function getCatalogStatus() {
    const response = await axiosInstance.get<ApiResponse<CatalogStatus>>("/catalog/status");
    return response.data.data;
}

export async function updateCatalog() {
    await axiosInstance.post("/catalog/update");
}
