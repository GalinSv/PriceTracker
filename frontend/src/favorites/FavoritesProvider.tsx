import { useCallback, useEffect, useState } from "react";
import type { ReactNode } from "react";

import { useAuth } from "../auth/AuthContext";
import { addFavorite, getFavoritesSummary, removeFavorite } from "../services/favoriteService";
import { FavoritesContext } from "./FavoritesContext";
import type { Favorites } from "./FavoritesContext";

// Which products are favorites of the logged in user, for the hearts and the menu badge
function FavoritesProvider({ children }: { children: ReactNode }) {
    const { username } = useAuth();
    const [ids, setIds] = useState<Set<number>>(new Set());
    const [newsCount, setNewsCount] = useState(0);

    const refresh = useCallback(() => {
        if (!username) return;
        getFavoritesSummary()
            .then((summary) => {
                setIds(new Set(summary.productIds));
                setNewsCount(summary.newsCount);
            })
            // The hearts stay empty; adding still works
            .catch(() => {});
    }, [username]);

    useEffect(refresh, [refresh]);

    const add = useCallback(async (productId: number) => {
        await addFavorite(productId);
        setIds((current) => new Set(current).add(productId));
    }, []);

    const remove = useCallback(
        async (productId: number) => {
            await removeFavorite(productId);
            setIds((current) => {
                const next = new Set(current);
                next.delete(productId);
                return next;
            });
            // The removed product may have been counted in the badge
            refresh();
        },
        [refresh],
    );

    const clearNews = useCallback(() => setNewsCount(0), []);

    const favorites: Favorites = {
        isFavorite: (productId) => productId != null && ids.has(productId),
        newsCount,
        add,
        remove,
        clearNews,
    };
    return <FavoritesContext.Provider value={favorites}>{children}</FavoritesContext.Provider>;
}

export default FavoritesProvider;
