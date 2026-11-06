import { createContext, useContext } from "react";

export type Favorites = {
    isFavorite: (productId: number | null) => boolean;
    // Favorites that changed since the last visit of the favorites page
    newsCount: number;
    add: (productId: number) => Promise<void>;
    remove: (productId: number) => Promise<void>;
    clearNews: () => void;
};

export const FavoritesContext = createContext<Favorites | null>(null);

export function useFavorites() {
    const favorites = useContext(FavoritesContext);
    if (!favorites) {
        throw new Error("useFavorites must be used inside FavoritesProvider");
    }
    return favorites;
}
