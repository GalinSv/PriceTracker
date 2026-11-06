import { useState } from "react";
import type { MouseEvent } from "react";

import { Button, CircularProgress, IconButton, Tooltip } from "@mui/material";
import FavoriteIcon from "@mui/icons-material/Favorite";
import FavoriteBorderIcon from "@mui/icons-material/FavoriteBorder";

import { useAuth } from "../../auth/AuthContext";
import { useFavorites } from "../../favorites/FavoritesContext";
import { notify } from "../../utils/notify";

type Props = {
    // The product the heart shows; null while a catalog block is not one product yet
    productId: number | null;
    // Called before adding, e.g. to turn a catalog block into a product
    resolveProductId?: () => Promise<number>;
    withLabel?: boolean;
};

function FavoriteButton({ productId, resolveProductId, withLabel = false }: Props) {
    const auth = useAuth();
    const favorites = useFavorites();
    const [busy, setBusy] = useState(false);
    const favorite = favorites.isFavorite(productId);

    async function handleClick(event: MouseEvent) {
        // The heart sits on a clickable card
        event.stopPropagation();
        if (!auth.username) {
            auth.login();
            return;
        }
        setBusy(true);
        try {
            if (favorite && productId != null) {
                await favorites.remove(productId);
                notify.info("Премахнат от любими");
            } else {
                const id = resolveProductId ? await resolveProductId() : productId;
                if (id == null) return;
                await favorites.add(id);
                notify.success("Добавен в любими");
            }
        } catch (error) {
            notify.error(error, "Любимите не можаха да бъдат променени");
        } finally {
            setBusy(false);
        }
    }

    const title = favorite ? "Премахни от любими" : "Добави в любими";
    const icon = favorite ? <FavoriteIcon sx={{ color: "error.main" }} /> : <FavoriteBorderIcon />;

    if (withLabel) {
        return (
            <Button variant="outlined" color="inherit" startIcon={icon} loading={busy} onClick={handleClick}>
                {favorite ? "В любими" : "Добави в любими"}
            </Button>
        );
    }
    return (
        <Tooltip title={title}>
            <IconButton aria-label={title} onClick={handleClick} disabled={busy}>
                {busy ? <CircularProgress size={22} /> : icon}
            </IconButton>
        </Tooltip>
    );
}

export default FavoriteButton;
