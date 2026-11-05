import axios from "axios";

import { getToken } from "../auth/keycloak";
import { API_BASE_URL } from "../config/api";

const axiosInstance = axios.create({
    baseURL: API_BASE_URL,
});

// Every request of a logged in user carries the Keycloak token
axiosInstance.interceptors.request.use(async (config) => {
    const token = await getToken();
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

export default axiosInstance;
