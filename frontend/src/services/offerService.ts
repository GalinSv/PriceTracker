import axiosInstance from "../api/axios";

import type {
    ApiResponse,
    PriceSnapshot,
    ProductDetails,
    RefreshResult,
} from "../types/api";

export async function importOffer(url: string) {
    const response = await axiosInstance.post<ApiResponse<ProductDetails>>("/offers/import", null, {
        params: { url },
    });
    return response.data.data;
}

export async function getOfferHistory(offerId: number) {
    const response = await axiosInstance.get<ApiResponse<PriceSnapshot[]>>(`/offers/${offerId}/history`);
    return response.data.data;
}

export async function refreshPrices() {
    const response = await axiosInstance.post<ApiResponse<RefreshResult>>("/offers/refresh");
    return response.data.data;
}
