import type { ReactNode } from "react";

import {
    Chip,
    IconButton,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableRow,
    Tooltip,
    Typography,
} from "@mui/material";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";

import type { CatalogEntry } from "../../types/api";
import { isAvailable } from "../../utils/catalogGroups";
import type { ModelOffer } from "../../utils/catalogGroups";
import { formatPrice } from "../../utils/format";

type Props = {
    // color is shown only with showColor
    offers: ModelOffer[];
    // The rows with this price are marked as the best offer
    lowestPrice: number | null;
    showColor?: boolean;
    // More buttons at the end of a row
    actions?: (entry: CatalogEntry) => ReactNode;
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
    if (inStock && entry.onOrder) {
        return (
            <Tooltip title="Магазинът го доставя за всяка поръчка, обикновено за 1-2 седмици">
                <Chip size="small" label="По поръчка" color="warning" variant="outlined" />
            </Tooltip>
        );
    }
    return (
        <Chip
            size="small"
            label={inStock ? "В наличност" : "Изчерпан"}
            color={inStock ? "success" : "default"}
            variant={inStock ? "filled" : "outlined"}
        />
    );
}

// The stores of a search result, one row per offer
function CatalogOfferTable({ offers, lowestPrice, showColor = false, actions }: Props) {
    return (
        <TableContainer>
            <Table size="small" sx={{ "& td": { px: { xs: 0.75, sm: 2 } }, "& td:last-child": { pl: { xs: 0, sm: 2 } } }}>
                <TableBody>
                    {offers.map(({ entry, color }) => {
                        const isLowest = isAvailable(entry) && entry.price === lowestPrice;
                        return (
                            <TableRow
                                key={entry.url}
                                sx={{
                                    ...(isLowest && { bgcolor: "rgba(15,110,86,0.05)" }),
                                    "&:last-child td": { borderBottom: 0 },
                                }}
                            >
                                <TableCell sx={{ fontWeight: 700, whiteSpace: { sm: "nowrap" } }}>
                                    {entry.storeName}
                                    {/* On a phone there is no room for a color column */}
                                    {showColor && color && (
                                        <Typography
                                            variant="caption"
                                            color="text.secondary"
                                            sx={{ display: { xs: "block", sm: "none" } }}
                                        >
                                            {color}
                                        </Typography>
                                    )}
                                </TableCell>
                                {/* On narrow screens there is no room; the product name is above anyway */}
                                <TableCell sx={{ maxWidth: 360, display: { xs: "none", md: "table-cell" } }}>
                                    <Typography variant="body2" noWrap title={entry.name}>
                                        {entry.name}
                                    </Typography>
                                </TableCell>
                                {showColor && (
                                    <TableCell sx={{ whiteSpace: "nowrap", display: { xs: "none", sm: "table-cell" } }}>
                                        <Typography variant="body2" color={color ? "text.primary" : "text.secondary"}>
                                            {color ?? "—"}
                                        </Typography>
                                    </TableCell>
                                )}
                                <TableCell>
                                    <StockStatus entry={entry} />
                                </TableCell>
                                <TableCell align="right" sx={{ whiteSpace: "nowrap" }}>
                                    <Typography sx={{ fontWeight: 700 }} color={isLowest ? "primary.main" : "text.primary"}>
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
                                        >
                                            <OpenInNewIcon fontSize="small" />
                                        </IconButton>
                                    </Tooltip>
                                    {actions?.(entry)}
                                </TableCell>
                            </TableRow>
                        );
                    })}
                </TableBody>
            </Table>
        </TableContainer>
    );
}

export default CatalogOfferTable;
