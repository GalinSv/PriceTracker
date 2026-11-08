import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { Alert, Box, Button, Card, Chip, InputAdornment, LinearProgress, TextField, Typography } from "@mui/material";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutlined";

import ProductImage from "../../components/product/ProductImage";
import { useFavorites } from "../../favorites/FavoritesContext";
import { getFavorites, markFavoritesSeen, setTargetPrice } from "../../services/favoriteService";
import type { Favorite } from "../../types/api";
import { extractErrorMessage } from "../../utils/errors";
import { formatPrice } from "../../utils/format";
import { notify } from "../../utils/notify";

type ChipInfo = {
    label: string;
    color: "success" | "warning" | "info" | "default";
    filled?: boolean;
};

// What changed since the last visit, and whether the target price is reached
function changeChips(item: Favorite): ChipInfo[] {
    const chips: ChipInfo[] = [];
    if (item.newStores.length > 0) {
        chips.push({ label: `Нов магазин: ${item.newStores.join(", ")}`, color: "info" });
    }
    const now = item.lowestPrice;
    const before = item.seenPrice;
    if (now !== before) {
        if (now == null) {
            chips.push({ label: "Изчерпан навсякъде", color: "default" });
        } else if (before == null) {
            chips.push({ label: "Вече е в наличност", color: "success" });
        } else if (now < before) {
            chips.push({ label: `Поевтиня от ${formatPrice(before)}`, color: "success" });
        } else {
            chips.push({ label: `Поскъпна от ${formatPrice(before)}`, color: "warning" });
        }
    }
    if (item.targetReached) {
        chips.push({ label: "Достигна желаната цена", color: "success", filled: true });
    }
    return chips;
}

function ChangeSinceAdded({ item }: { item: Favorite }) {
    const now = item.lowestPrice;
    const then = item.priceWhenAdded;
    if (now == null || then == null) return null;
    const diff = now - then;
    if (Math.abs(diff) < 0.005) {
        return (
            <Typography variant="body2" color="text.secondary">
                Без промяна от добавянето
            </Typography>
        );
    }
    const percent = Math.round((diff / then) * 100);
    return (
        <Typography variant="body2" sx={{ fontWeight: 600 }} color={diff < 0 ? "success.main" : "error.main"}>
            {diff < 0 ? "▼" : "▲"} {formatPrice(Math.abs(diff))}
            {percent !== 0 && ` (${percent > 0 ? "+" : ""}${percent}%)`} от добавянето
        </Typography>
    );
}

// "1500,50", as prices are written in Bulgarian
function priceText(price: number | null) {
    return price != null ? price.toFixed(2).replace(".", ",") : "";
}

function TargetPriceField({ item, onSaved }: { item: Favorite; onSaved: (updated: Favorite) => void }) {
    const [text, setText] = useState(priceText(item.targetPrice));
    const [saving, setSaving] = useState(false);

    async function save() {
        const trimmed = text.trim().replace(",", ".");
        const value = trimmed === "" ? null : Number(trimmed);
        if (value != null && (!Number.isFinite(value) || value <= 0)) {
            notify.error(null, "Желаната цена трябва да е число над нула");
            return;
        }
        if (value === item.targetPrice) return;
        setSaving(true);
        try {
            const updated = await setTargetPrice(item.productId, value);
            setText(priceText(updated.targetPrice));
            onSaved(updated);
        } catch (error) {
            notify.error(error, "Желаната цена не можа да бъде запазена");
        } finally {
            setSaving(false);
        }
    }

    return (
        <TextField
            size="small"
            label="Желана цена"
            placeholder="няма"
            value={text}
            disabled={saving}
            onChange={(event) => setText(event.target.value)}
            onBlur={save}
            onKeyDown={(event) => {
                // Saved by onBlur
                if (event.key === "Enter") (event.target as HTMLInputElement).blur();
            }}
            slotProps={{
                input: { endAdornment: <InputAdornment position="end">€</InputAdornment> },
                htmlInput: { inputMode: "decimal" },
                inputLabel: { shrink: true },
            }}
            sx={{ width: 150 }}
        />
    );
}

function FavoriteRow({
    item,
    onSaved,
    onRemove,
}: {
    item: Favorite;
    onSaved: (updated: Favorite) => void;
    onRemove: (item: Favorite) => void;
}) {
    const chips = changeChips(item);
    return (
        <Card
            sx={{
                p: 2,
                display: "flex",
                flexWrap: { xs: "wrap", sm: "nowrap" },
                gap: 2,
                alignItems: "flex-start",
                ...(item.news && { borderColor: "primary.main" }),
            }}
        >
            <Box component={Link} to={`/products/${item.productId}`} sx={{ width: { xs: 72, sm: 96 }, flexShrink: 0 }}>
                <ProductImage src={item.imageUrl} alt={item.name} height={90} />
            </Box>

            <Box sx={{ flex: 1, minWidth: 0 }}>
                {item.brand && (
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, display: "block" }}>
                        {item.brand}
                    </Typography>
                )}
                <Typography
                    component={Link}
                    to={`/products/${item.productId}`}
                    sx={{
                        fontWeight: 700,
                        color: "text.primary",
                        textDecoration: "none",
                        "&:hover": { textDecoration: "underline" },
                    }}
                >
                    {item.name}
                </Typography>
                {chips.length > 0 && (
                    <Box sx={{ display: "flex", gap: 0.75, flexWrap: "wrap", mt: 1 }}>
                        {chips.map((chip) => (
                            <Chip
                                key={chip.label}
                                size="small"
                                label={chip.label}
                                color={chip.color}
                                variant={chip.filled ? "filled" : "outlined"}
                            />
                        ))}
                    </Box>
                )}
                <Box sx={{ display: "flex", gap: 1, alignItems: "center", flexWrap: "wrap", mt: 1.5 }}>
                    <TargetPriceField item={item} onSaved={onSaved} />
                    <Button size="small" color="inherit" startIcon={<DeleteOutlineIcon />} onClick={() => onRemove(item)}>
                        Премахни
                    </Button>
                </Box>
            </Box>

            <Box sx={{ flexShrink: 0, width: { xs: "100%", sm: "auto" }, textAlign: { xs: "left", sm: "right" } }}>
                <Typography variant="h6" color={item.lowestPrice != null ? "primary.main" : "text.secondary"}>
                    {item.lowestPrice != null ? formatPrice(item.lowestPrice) : "Няма наличност"}
                </Typography>
                <ChangeSinceAdded item={item} />
                <Typography variant="body2" color="text.secondary">
                    налично в {item.inStockCount} от {item.offerCount} {item.offerCount === 1 ? "магазин" : "магазина"}
                </Typography>
            </Box>
        </Card>
    );
}

function FavoritesPage() {
    const { clearNews, remove } = useFavorites();
    const [items, setItems] = useState<Favorite[] | null>(null);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;
        getFavorites()
            .then((loaded) => {
                if (cancelled) return;
                setItems(loaded);
                // The changes stay on the screen, but next time they are no longer news
                markFavoritesSeen()
                    .then(clearNews)
                    .catch(() => {});
            })
            .catch((e) => !cancelled && setError(extractErrorMessage(e, "Любимите не можаха да бъдат заредени")));
        return () => {
            cancelled = true;
        };
    }, [clearNews]);

    // Only the target price changes; the chips of this visit stay
    function handleSaved(updated: Favorite) {
        setItems((current) =>
            current &&
            current.map((item) =>
                item.productId === updated.productId
                    ? { ...item, targetPrice: updated.targetPrice, targetReached: updated.targetReached }
                    : item,
            ),
        );
    }

    async function handleRemove(item: Favorite) {
        try {
            await remove(item.productId);
            setItems((current) => current && current.filter((other) => other.productId !== item.productId));
            notify.info("Премахнат от любими");
        } catch (e) {
            notify.error(e, "Продуктът не можа да бъде премахнат");
        }
    }

    if (error) {
        return <Alert severity="error">{error}</Alert>;
    }
    if (!items) {
        return <LinearProgress />;
    }

    return (
        <Box>
            <Typography component="h1" variant="h4" sx={{ mb: 0.5 }}>
                Любими
            </Typography>
            <Typography color="text.secondary" sx={{ mb: 3 }}>
                Цените се проверяват автоматично. Промените са спрямо последното ти влизане тук.
            </Typography>

            {items.length === 0 ? (
                <Alert severity="info">
                    Нямаш любими продукти. Натисни сърцето на продукт в Каталог или на страницата на продукта.
                </Alert>
            ) : (
                <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
                    {items.map((item) => (
                        <FavoriteRow key={item.productId} item={item} onSaved={handleSaved} onRemove={handleRemove} />
                    ))}
                </Box>
            )}
        </Box>
    );
}

export default FavoritesPage;
