import { Route, Routes } from "react-router-dom";

import AdminOnly from "./auth/AdminOnly";
import RequireLogin from "./auth/RequireLogin";
import MainLayout from "./components/layout/MainLayout";

import SearchPage from "./pages/public/SearchPage";
import ProductPage from "./pages/public/ProductPage";
import NotFoundPage from "./pages/public/NotFoundPage";

import FavoritesPage from "./pages/user/FavoritesPage";

import StoresPage from "./pages/admin/StoresPage";

function App() {
    return (
        <Routes>
            <Route path="/" element={<MainLayout />}>
                <Route index element={<SearchPage />} />
                <Route path="products/:id" element={<ProductPage />} />
                <Route
                    path="favorites"
                    element={
                        <RequireLogin message="Влез в акаунта си, за да виждаш любимите си продукти.">
                            <FavoritesPage />
                        </RequireLogin>
                    }
                />
                <Route
                    path="stores"
                    element={
                        <AdminOnly>
                            <StoresPage />
                        </AdminOnly>
                    }
                />
                <Route path="*" element={<NotFoundPage />} />
            </Route>
        </Routes>
    );
}

export default App;
