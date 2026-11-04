import { Route, Routes } from "react-router-dom";

import MainLayout from "./components/layout/MainLayout";

import SearchPage from "./pages/public/SearchPage";
import ProductPage from "./pages/public/ProductPage";
import NotFoundPage from "./pages/public/NotFoundPage";

import StoresPage from "./pages/admin/StoresPage";

function App() {
    return (
        <Routes>
            <Route path="/" element={<MainLayout />}>
                <Route index element={<SearchPage />} />
                <Route path="products/:id" element={<ProductPage />} />
                <Route path="stores" element={<StoresPage />} />
                <Route path="*" element={<NotFoundPage />} />
            </Route>
        </Routes>
    );
}

export default App;
