import axiosInstance from "../api/axios";

import type { ApiResponse, Store } from "../types/api";

export async function getStores() {
    const response = await axiosInstance.get<ApiResponse<Store[]>>("/stores");
    return response.data.data;
}
