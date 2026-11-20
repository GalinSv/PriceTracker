import { useRef, useState } from "react";

import {
    Box,
    Button,
    Card,
    CardActionArea,
    CircularProgress,
    Collapse,
    LinearProgress,
    Tooltip,
    Typography,
} from "@mui/material";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import DragIndicatorIcon from "@mui/icons-material/DragIndicator";
import ExpandLessIcon from "@mui/icons-material/ExpandLess";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";

import type { CatalogEntry } from "../../types/api";
import { isAvailable } from "../../utils/catalogGroups";
import type { ProductGroup } from "../../utils/catalogGroups";
import { formatPrice } from "../../utils/format";
import FavoriteButton from "../favorites/FavoriteButton";
import ProductImage from "../product/ProductImage";
import CatalogOfferTable from "./CatalogOfferTable";

// How an admin joins two products: drags one by its handle and drops it on the other.
// On a touch screen, where nothing can be dragged: a tap on the handle picks the product, then "Събери тук"
// on the other one.
export type Joining = {
    // Another product is being dragged or was picked, and can be put with this one
    canDrop: boolean;
    // This product was picked with a tap on its handle
    picked: boolean;
    onPick: (group: ProductGroup) => void;
    onDragStart: (group: ProductGroup) => void;
    onDragEnd: () => void;
    onDrop: (target: ProductGroup) => void;
    // Takes a store that was moved here out again
    onSeparate: (group: ProductGroup, entry: CatalogEntry) => void;
};

type Props = {
    group: ProductGroup;
    // Opens the product page with all its stores
    onOpen: (group: ProductGroup) => void;
    opening: boolean;
    // Makes the block's links one product and gives its id
    toProduct: (group: ProductGroup) => Promise<number>;
    // null for everyone but admins
    joining: Joining | null;
    // The stores are shown; closed until the visitor opens them
    expanded: boolean;
    onToggle: (group: ProductGroup) => void;
};

// A search result shown like the product page: the product on top, its stores below, cheapest first
function ProductGroupCard({ group, onOpen, opening, toProduct, joining, expanded, onToggle }: Props) {
    const [showUnavailable, setShowUnavailable] = useState(false);
    const card = useRef<HTMLDivElement>(null);
    // The dragged product is over this one
    const [dropHover, setDropHover] = useState(false);
    const canDrop = joining?.canDrop ?? false;

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
        <Card
            ref={card}
            sx={{
                position: "relative",
                ...(joining?.picked && { outline: "2px solid", outlineColor: "primary.main" }),
                ...(canDrop && {
                    outline: "2px dashed",
                    outlineColor: dropHover ? "primary.main" : "divider",
                    ...(dropHover && { bgcolor: "rgba(15,110,86,0.05)" }),
                }),
            }}
            onDragOver={(event) => {
                if (!canDrop) return;
                // Without this the browser doesn't allow a drop here
                event.preventDefault();
                event.dataTransfer.dropEffect = "move";
                setDropHover(true);
            }}
            onDragLeave={(event) => {
                // Moving over the card's own rows and buttons also fires this
                if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setDropHover(false);
            }}
            onDrop={(event) => {
                if (!canDrop) return;
                event.preventDefault();
                setDropHover(false);
                joining?.onDrop(group);
            }}
        >
            {joining && (
                <Tooltip title="Хвани и пусни върху друг продукт, за да ги събереш в един. Или натисни тук и после „Събери тук“ на другия.">
                    <Box
                        draggable
                        onClick={() => joining.onPick(group)}
                        onDragStart={(event) => {
                            event.dataTransfer.effectAllowed = "move";
                            event.dataTransfer.setData("text/plain", group.name);
                            // The whole card follows the mouse, not only the handle
                            if (card.current) event.dataTransfer.setDragImage(card.current, 16, 16);
                            joining.onDragStart(group);
                        }}
                        onDragEnd={joining.onDragEnd}
                        sx={{
                            position: "absolute",
                            top: 4,
                            left: 4,
                            zIndex: 1,
                            display: "flex",
                            p: 0.25,
                            borderRadius: 1,
                            color: "text.secondary",
                            bgcolor: "background.paper",
                            cursor: "grab",
                            "&:hover": { color: "primary.main" },
                        }}
                    >
                        <DragIndicatorIcon fontSize="small" />
                    </Box>
                </Tooltip>
            )}
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

            <Box
                sx={{
                    px: 1,
                    py: 0.5,
                    borderTop: 1,
                    borderColor: "divider",
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                }}
            >
                <Button
                    size="small"
                    endIcon={expanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                    onClick={() => onToggle(group)}
                >
                    {expanded ? "Скрий магазините" : `Виж магазините (${shownRows.length})`}
                </Button>
                <Box sx={{ display: "flex", gap: 1 }}>
                    {canDrop && (
                        <Button size="small" variant="contained" onClick={() => joining?.onDrop(group)}>
                            Събери тук
                        </Button>
                    )}
                    {/* On a phone the header has no room for it */}
                    <Button
                        size="small"
                        endIcon={<ChevronRightIcon />}
                        onClick={() => onOpen(group)}
                        disabled={opening}
                        sx={{ display: { xs: "inline-flex", sm: "none" } }}
                    >
                        Всички цени
                    </Button>
                </Box>
            </Box>
            <Collapse in={expanded} unmountOnExit>
                <CatalogOfferTable
                    offers={shownRows.map((entry) => ({ entry, color: null }))}
                    lowestPrice={group.lowestPrice}
                    actions={(entry) =>
                        joining &&
                        entry.joinedToUrl &&
                        entry.productId == null && (
                            <Tooltip title="Този магазин е добавен тук на ръка. Върни го като отделен продукт">
                                <Button size="small" onClick={() => joining.onSeparate(group, entry)}>
                                    Отдели
                                </Button>
                            </Tooltip>
                        )
                    }
                />
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
            </Collapse>
        </Card>
    );
}

export default ProductGroupCard;
