import { useState } from "react";

import { Box, Button, Card, Chip, LinearProgress, Typography } from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import ExpandLessIcon from "@mui/icons-material/ExpandLess";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";

import { isAvailable, modelOffers, offerColors } from "../../utils/catalogGroups";
import type { ModelGroup } from "../../utils/catalogGroups";
import { formatPrice } from "../../utils/format";
import ProductImage from "../product/ProductImage";
import CatalogOfferTable from "./CatalogOfferTable";

type Props = {
    // null = the search has no such model (yet, while the prices are still being checked)
    model: ModelGroup | null;
    // Store pages are still being opened, so offers can still come
    checking: boolean;
    onBack: () => void;
};

// The page behind "Всички цени" of a model: the offers of all its colors in one table, cheapest first
function ModelOffers({ model, checking, onBack }: Props) {
    // null = all colors
    const [color, setColor] = useState<string | null>(null);
    const [showUnavailable, setShowUnavailable] = useState(false);

    const back = (
        <Button startIcon={<ArrowBackIcon />} onClick={onBack} sx={{ mb: 2 }}>
            Към резултатите
        </Button>
    );

    if (!model) {
        return (
            <Box>
                {back}
                {checking ? (
                    <LinearProgress />
                ) : (
                    <Typography color="text.secondary">
                        Този модел вече не е сред резултатите от търсенето.
                    </Typography>
                )}
            </Box>
        );
    }

    const offers = modelOffers(model);
    const colors = offerColors(offers);
    // A color that left the results while the prices load doesn't hide everything
    const chosen = color != null && colors.includes(color) ? color : null;
    const ofColor = chosen == null ? offers : offers.filter((offer) => offer.color === chosen);
    const available = ofColor.filter((offer) => isAvailable(offer.entry));
    const unavailable = ofColor.filter((offer) => !isAvailable(offer.entry));
    const lowestPrice = available[0]?.entry.price ?? null;
    const storeCount = new Set(available.map((offer) => offer.entry.storeCode)).size;
    const shown = showUnavailable || available.length === 0 ? ofColor : available;

    return (
        <Box>
            {back}
            <Box sx={{ display: "flex", gap: 2, alignItems: "center", mb: 2 }}>
                <Box sx={{ width: { xs: 88, sm: 140 }, flexShrink: 0 }}>
                    <ProductImage src={model.imageUrl} alt={model.name} height={130} />
                </Box>
                <Box sx={{ minWidth: 0 }}>
                    {model.brand && (
                        <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                            {model.brand}
                        </Typography>
                    )}
                    <Typography component="h1" variant="h5" sx={{ fontWeight: 700 }}>
                        {model.name}
                    </Typography>
                    <Typography variant="h6" color="primary.main" sx={{ mt: 0.5 }}>
                        {lowestPrice != null ? `от ${formatPrice(lowestPrice)}` : "Няма наличност"}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        {available.length} {available.length === 1 ? "оферта" : "оферти"} в наличност в {storeCount}{" "}
                        {storeCount === 1 ? "магазин" : "магазина"}
                    </Typography>
                </Box>
            </Box>

            {colors.length > 1 && (
                <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap", mb: 2 }}>
                    <Chip
                        label="Всички цветове"
                        color={chosen == null ? "primary" : "default"}
                        variant={chosen == null ? "filled" : "outlined"}
                        onClick={() => setColor(null)}
                    />
                    {colors.map((name) => (
                        <Chip
                            key={name}
                            label={name}
                            color={chosen === name ? "primary" : "default"}
                            variant={chosen === name ? "filled" : "outlined"}
                            onClick={() => setColor(name)}
                        />
                    ))}
                </Box>
            )}

            {checking && <LinearProgress sx={{ mb: 2 }} />}

            <Card>
                <CatalogOfferTable offers={shown} lowestPrice={lowestPrice} showColor />
                {available.length > 0 && unavailable.length > 0 && (
                    <Box sx={{ px: 1, py: 0.5, borderTop: 1, borderColor: "divider" }}>
                        <Button
                            size="small"
                            endIcon={showUnavailable ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                            onClick={() => setShowUnavailable((value) => !value)}
                        >
                            {showUnavailable
                                ? "Скрий офертите без наличност"
                                : `Още ${unavailable.length} ${unavailable.length === 1 ? "оферта" : "оферти"} без наличност`}
                        </Button>
                    </Box>
                )}
            </Card>
        </Box>
    );
}

export default ModelOffers;
