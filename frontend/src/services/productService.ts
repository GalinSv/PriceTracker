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
