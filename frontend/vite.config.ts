import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
    plugins: [react()],
    server: {
        port: 5173,
        strictPort: true,
        // The browser calls /api on the same address and Vite forwards it to Spring Boot, so no CORS setup is needed
        proxy: {
            "/api": "http://localhost:8081",
        },
    },
});
