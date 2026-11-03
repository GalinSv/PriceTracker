import {
    Chip,
    IconButton,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Tooltip,
    Typography,
} from "@mui/material";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";

import type { Offer } from "../../types/api";
import { formatDateTime, formatPrice } from "../../utils/format";

type Props = {
    offers: Offer[];
    lowestPrice: number | null;
};

function OffersTable({ offers, lowestPrice }: Props) {
    return (
        <TableContainer>
            <Table>
                <TableHead>
                    <TableRow>
                        <TableCell>Магазин</TableCell>
                        <TableCell>Име в магазина</TableCell>
                        <TableCell>Наличност</TableCell>
                        <TableCell align="right">Цена</TableCell>
                        <TableCell>Проверено</TableCell>
                        <TableCell />
                    </TableRow>
                </TableHead>
                <TableBody>
                    {offers.map((offer) => {
                        const isLowest =
                            offer.inStock && lowestPrice != null && offer.currentPrice === lowestPrice;
                        return (
                            <TableRow key={offer.id} sx={isLowest ? { bgcolor: "rgba(15,110,86,0.05)" } : undefined}>
                                <TableCell sx={{ fontWeight: 700 }}>{offer.storeName}</TableCell>
                                <TableCell sx={{ maxWidth: 360 }}>
                                    <Typography variant="body2" noWrap title={offer.title}>
                                        {offer.title}
                                    </Typography>
                                </TableCell>
                                <TableCell>
                                    <Chip
                                        size="small"
                                        label={offer.inStock ? "В наличност" : "Изчерпан"}
                                        color={offer.inStock ? "success" : "default"}
                                        variant={offer.inStock ? "filled" : "outlined"}
                                    />
                                </TableCell>
                                <TableCell align="right">
                                    <Typography sx={{ fontWeight: 700 }} color={isLowest ? "primary.main" : "text.primary"}>
                                        {formatPrice(offer.currentPrice, offer.currency)}
                                    </Typography>
                                    {isLowest && (
                                        <Typography variant="caption" color="primary.main">
                                            най-ниска
                                        </Typography>
                                    )}
                                </TableCell>
                                <TableCell sx={{ whiteSpace: "nowrap", color: "text.secondary" }}>
                                    {formatDateTime(offer.lastCheckedAt)}
                                </TableCell>
                                <TableCell align="right">
                                    <Tooltip title="Към магазина">
                                        <IconButton
                                            component="a"
                                            href={offer.url}
                                            target="_blank"
                                            rel="noopener noreferrer"
                                            size="small"
                                        >
                                            <OpenInNewIcon fontSize="small" />
                                        </IconButton>
                                    </Tooltip>
                                </TableCell>
                            </TableRow>
                        );
                    })}
                </TableBody>
            </Table>
        </TableContainer>
    );
}

export default OffersTable;
