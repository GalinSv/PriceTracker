import { Box, Button, Card, CardActionArea, Chip, Collapse, Typography } from "@mui/material";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import ExpandLessIcon from "@mui/icons-material/ExpandLess";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";

import { isAvailable, modelOffers, offerColors } from "../../utils/catalogGroups";
import type { ModelGroup } from "../../utils/catalogGroups";
import { formatPrice } from "../../utils/format";
import ProductImage from "../product/ProductImage";
import CatalogOfferTable from "./CatalogOfferTable";

type Props = {
    model: ModelGroup;
    // Opens the page with the offers of all colors
    onOpen: (model: ModelGroup) => void;
    expanded: boolean;
    onToggle: (model: ModelGroup) => void;
};

// A model in all its colors as one search result. The blocks below still show each color on its own.
function ModelGroupCard({ model, onOpen, expanded, onToggle }: Props) {
    const available = modelOffers(model).filter((offer) => isAvailable(offer.entry));
    const colors = offerColors(available);
    // One row per store: its cheapest color. The offers are cheapest first.
    const stores = new Set<string>();
    const rows = available.filter((offer) => {
        if (stores.has(offer.entry.storeCode)) return false;
        stores.add(offer.entry.storeCode);
        return true;
    });

    return (
        <Card sx={{ border: 1, borderColor: "primary.main" }}>
            <CardActionArea
                onClick={() => onOpen(model)}
                sx={{ display: "flex", gap: 2, p: 2, alignItems: "center", justifyContent: "flex-start" }}
            >
                <Box sx={{ width: { xs: 88, sm: 120 }, flexShrink: 0 }}>
                    <ProductImage src={model.imageUrl} alt={model.name} height={110} />
                </Box>
                <Box sx={{ minWidth: 0, flex: 1 }}>
                    <Box sx={{ display: "flex", gap: 1, alignItems: "center", flexWrap: "wrap" }}>
                        {model.brand && (
                            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                                {model.brand}
                            </Typography>
                        )}
                        <Chip
                            size="small"
                            color="primary"
                            variant="outlined"
                            label={colors.length > 1 ? "Всички цветове" : "Всички оферти"}
                        />
                    </Box>
                    <Typography sx={{ fontWeight: 700, fontSize: "1.1rem", lineHeight: 1.3 }}>{model.name}</Typography>
                    <Typography variant="h6" color="primary.main" sx={{ mt: 0.5 }}>
                        от {formatPrice(model.lowestPrice)}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        {colors.length > 0 ? `${colors.join(", ")} · ` : ""}в наличност в {rows.length}{" "}
                        {rows.length === 1 ? "магазин" : "магазина"}
                    </Typography>
                </Box>
                <Box
                    sx={{
                        display: { xs: "none", sm: "flex" },
                        alignItems: "center",
                        gap: 0.5,
                        color: "primary.main",
                        fontWeight: 600,
                        flexShrink: 0,
                    }}
                >
                    Всички цени
                    <ChevronRightIcon />
                </Box>
            </CardActionArea>

            <Box
                sx={{
                    px: 1,
                    py: 0.5,
                    borderTop: 1,
                    borderColor: "divider",
                    display: "flex",
                    justifyContent: "space-between",
                }}
            >
                <Button
                    size="small"
                    endIcon={expanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                    onClick={() => onToggle(model)}
                >
                    {expanded ? "Скрий магазините" : `Виж магазините (${rows.length})`}
                </Button>
                {/* On a phone the header has no room for it */}
                <Button
                    size="small"
                    endIcon={<ChevronRightIcon />}
                    onClick={() => onOpen(model)}
                    sx={{ display: { xs: "inline-flex", sm: "none" } }}
                >
                    Всички цени
                </Button>
            </Box>
            <Collapse in={expanded} unmountOnExit>
                <CatalogOfferTable offers={rows} lowestPrice={model.lowestPrice} showColor />
            </Collapse>
        </Card>
    );
}

export default ModelGroupCard;
