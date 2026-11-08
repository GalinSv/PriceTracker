import axiosInstance from "../api/axios";

import type { ApiResponse, Favorite, FavoritesSummary } from "../types/api";

export async function getFavorites() {
    const response = await axiosInstance.get<ApiResponse<Favorite[]>>("/favorites");
    return response.data.data;
}

export async function getFavoritesSummary() {
    const response = await axiosInstance.get<ApiResponse<FavoritesSummary>>("/favorites/summary");
    return response.data.data;
}

export async function addFavorite(productId: number) {
    const response = await axiosInstance.post<ApiResponse<Favorite>>(`/favorites/${productId}`);
    return response.data.data;
}

export async function removeFavorite(productId: number) {
    await axiosInstance.delete(`/favorites/${productId}`);
}

// null removes the target price
export async function setTargetPrice(productId: number, targetPrice: number | null) {
    const response = await axiosInstance.put<ApiResponse<Favorite>>(`/favorites/${productId}/target-price`, {
        targetPrice,
    });
    return response.data.data;
}

export async function markFavoritesSeen() {
    await axiosInstance.post("/favorites/seen");
}
