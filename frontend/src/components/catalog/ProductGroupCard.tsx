import { useState } from "react";
import { Link } from "react-router-dom";

import {
    Box,
    Button,
    Card,
    CardActionArea,
    Chip,
    CircularProgress,
    IconButton,
    LinearProgress,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableRow,
    Tooltip,
    Typography,
} from "@mui/material";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import ExpandLessIcon from "@mui/icons-material/ExpandLess";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import VisibilityIcon from "@mui/icons-material/Visibility";

import type { CatalogEntry } from "../../types/api";
import { isAvailable } from "../../utils/catalogGroups";
import type { ProductGroup } from "../../utils/catalogGroups";
import { formatPrice } from "../../utils/format";
import FavoriteButton from "../favorites/FavoriteButton";
import ProductImage from "../product/ProductImage";

type Props = {
    group: ProductGroup;
    importingUrl: string | null;
    onTrack: (entry: CatalogEntry) => void;
    // Opens the product page with all its stores
    onOpen: (group: ProductGroup) => void;
    opening: boolean;
    // Makes the block's links one product and gives its id
    toProduct: (group: ProductGroup) => Promise<number>;
};

function StockStatus({ entry }: { entry: CatalogEntry }) {
    if (entry.price == null) {
        return (
            <Typography variant="body2" color="text.secondary">
                Няма цена
            </Typography>
        );
    }
    const inStock = entry.inStock !== false;
    return (
        <Chip
            size="small"
            label={inStock ? "В наличност" : "Изчерпан"}
            color={inStock ? "success" : "default"}
            variant={inStock ? "filled" : "outlined"}
        />
    );
}

// A search result shown like the product page: the product on top, its stores below, cheapest first
function ProductGroupCard({ group, importingUrl, onTrack, onOpen, opening, toProduct }: Props) {
    const [showUnavailable, setShowUnavailable] = useState(false);

    // One row per store: some stores list the same product under several links.
    // The entries are in stock and cheapest first, so the first link of a store is its best offer.
    const seenStores = new Set<string>();
    const rows = group.entries.filter((entry) => {
        if (seenStores.has(entry.storeCode)) return false;
        seenStores.add(entry.storeCode);
        return true;
    });
    const available = rows.filter(isAvailable);
    // A product in stock somewhere shows only those stores; the rest open on request
    const unavailable = available.length > 0 ? rows.filter((entry) => !isAvailable(entry)) : [];
    const shownRows = available.length > 0 && !showUnavailable ? available : rows;

    return (
        <Card sx={{ position: "relative" }}>
            {/* Outside the clickable header: a button can't be inside another button */}
            <Box sx={{ position: "absolute", top: 8, right: 8, zIndex: 1 }}>
                <FavoriteButton productId={group.productId} resolveProductId={() => toProduct(group)} />
            </Box>
            <CardActionArea
                onClick={() => onOpen(group)}
                disabled={opening}
                sx={{ display: "flex", gap: 2, p: 2, alignItems: "center", justifyContent: "flex-start" }}
            >
                <Box sx={{ width: { xs: 88, sm: 120 }, flexShrink: 0 }}>
                    <ProductImage src={group.imageUrl} alt={group.name} height={110} />
                </Box>
                <Box sx={{ minWidth: 0, flex: 1, pr: { xs: 4, sm: 0 } }}>
                    {group.brand && (
                        <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                            {group.brand}
                        </Typography>
                    )}
                    <Typography sx={{ fontWeight: 700, fontSize: "1.1rem", lineHeight: 1.3 }}>{group.name}</Typography>
                    <Typography variant="h6" color="primary.main" sx={{ mt: 0.5 }}>
                        {group.lowestPrice != null ? `от ${formatPrice(group.lowestPrice)}` : "Няма наличност"}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        в наличност в {available.length} от {rows.length} {rows.length === 1 ? "магазин" : "магазина"}
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
                    {opening ? <CircularProgress size={18} /> : "Всички цени"}
                    <ChevronRightIcon />
                </Box>
            </CardActionArea>
            {opening && <LinearProgress />}

            <TableContainer>
                <Table size="small">
                    <TableBody>
                        {shownRows.map((entry) => {
                            const isLowest = isAvailable(entry) && entry.price === group.lowestPrice;
                            return (
                                <TableRow
                                    key={entry.url}
                                    sx={{
                                        ...(isLowest && { bgcolor: "rgba(15,110,86,0.05)" }),
                                        "&:last-child td": { borderBottom: 0 },
                                    }}
                                >
                                    <TableCell sx={{ fontWeight: 700, whiteSpace: "nowrap" }}>{entry.storeName}</TableCell>
                                    {/* On narrow screens there is no room; the product name is above anyway */}
                                    <TableCell sx={{ maxWidth: 360, display: { xs: "none", md: "table-cell" } }}>
                                        <Typography variant="body2" noWrap title={entry.name}>
                                            {entry.name}
                                        </Typography>
                                    </TableCell>
                                    <TableCell>
                                        <StockStatus entry={entry} />
                                    </TableCell>
                                    <TableCell align="right" sx={{ whiteSpace: "nowrap" }}>
                                        <Typography
                                            sx={{ fontWeight: 700 }}
                                            color={isLowest ? "primary.main" : "text.primary"}
                                        >
                                            {formatPrice(entry.price, entry.currency ?? undefined)}
                                        </Typography>
                                    </TableCell>
                                    <TableCell align="right" sx={{ whiteSpace: "nowrap" }}>
                                        <Tooltip title="Отвори в магазина">
                                            <IconButton
                                                component="a"
                                                href={entry.url}
                                                target="_blank"
                                                rel="noopener noreferrer"
                                                size="small"
                                                sx={{ mr: 1 }}
                                            >
                                                <OpenInNewIcon fontSize="small" />
                                            </IconButton>
                                        </Tooltip>
                                        {entry.productId != null ? (
                                            <Button
                                                component={Link}
                                                to={`/products/${entry.productId}`}
                                                variant="outlined"
                                                size="small"
                                                sx={{ minWidth: 100 }}
                                            >
                                                Виж цените
                                            </Button>
                                        ) : (
                                            <Button
                                                variant="contained"
                                                size="small"
                                                startIcon={<VisibilityIcon />}
                                                loading={importingUrl === entry.url}
                                                disabled={importingUrl != null && importingUrl !== entry.url}
                                                onClick={() => onTrack(entry)}
                                                sx={{ minWidth: 100 }}
                                            >
                                                Следи
                                            </Button>
                                        )}
                                    </TableCell>
                                </TableRow>
                            );
                        })}
                    </TableBody>
                </Table>
            </TableContainer>
            {unavailable.length > 0 && (
                <Box sx={{ px: 1, py: 0.5, borderTop: 1, borderColor: "divider" }}>
                    <Button
                        size="small"
                        endIcon={showUnavailable ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                        onClick={() => setShowUnavailable((shown) => !shown)}
                    >
                        {showUnavailable
                            ? "Скрий магазините без наличност"
                            : `Още ${unavailable.length} ${unavailable.length === 1 ? "магазин" : "магазина"} без наличност`}
                    </Button>
                </Box>
            )}
        </Card>
    );
}

export default ProductGroupCard;
