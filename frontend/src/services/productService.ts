import axiosInstance from "../api/axios";

import type { ApiResponse, ProductDetails, ProductSummary } from "../types/api";

export async function searchProducts(query: string) {
    const response = await axiosInstance.get<ApiResponse<ProductSummary[]>>("/products", {
        params: { query },
    });
    return response.data.data;
}

export async function getProduct(id: number) {
    const response = await axiosInstance.get<ApiResponse<ProductDetails>>(`/products/${id}`);
    return response.data.data;
}

// Looks for stores that sell the product but are not on its page yet; can take several seconds
export async function findNewStores(id: number) {
    const response = await axiosInstance.post<ApiResponse<ProductDetails>>(`/products/${id}/find-stores`);
    return response.data.data;
}
