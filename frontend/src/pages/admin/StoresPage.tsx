import { useEffect, useState } from "react";

import {
    Box,
    Button,
    Card,
    CardContent,
    Chip,
    LinearProgress,
    Link,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from "@mui/material";
import CloudSyncIcon from "@mui/icons-material/CloudSync";
import FilterListIcon from "@mui/icons-material/FilterList";
import RefreshIcon from "@mui/icons-material/Refresh";

import { getCatalogStatus, refreshCatalogFilters, updateCatalog } from "../../services/catalogService";
import { refreshPrices } from "../../services/offerService";
import { getStores } from "../../services/storeService";
import type { CatalogStatus, Store, StoreStatus } from "../../types/api";
import { formatDateTime, formatNumber } from "../../utils/format";
import { notify } from "../../utils/notify";

const STATUS_LABELS: Record<StoreStatus, { label: string; color: "success" | "default" | "error" }> = {
    ACTIVE: { label: "Активен", color: "success" },
    DISABLED: { label: "Спрян", color: "default" },
    BLOCKED: { label: "Блокира ботове", color: "error" },
};

const CATALOG_POLL_MS = 5000;

function StoresPage() {
    const [stores, setStores] = useState<Store[]>([]);
    const [catalog, setCatalog] = useState<CatalogStatus | null>(null);
    const [refreshing, setRefreshing] = useState(false);

    useEffect(() => {
        Promise.all([getStores(), getCatalogStatus()])
            .then(([loadedStores, loadedCatalog]) => {
                setStores(loadedStores);
                setCatalog(loadedCatalog);
            })
            .catch((error) => notify.error(error, "Магазините не можаха да бъдат заредени"));
    }, []);

    // While the catalog downloads in the background, check its progress every few seconds
    const catalogUpdating = catalog?.updating ?? false;
    useEffect(() => {
        if (!catalogUpdating) return;
        const timer = setInterval(() => {
            getCatalogStatus().then(setCatalog).catch(() => undefined);
        }, CATALOG_POLL_MS);
        return () => clearInterval(timer);
    }, [catalogUpdating]);

    async function handleRefreshPrices() {
        setRefreshing(true);
        try {
            const result = await refreshPrices();
            notify.success(
                `Проверени: ${result.checked}, променени: ${result.changed}, ` +
                    `изчезнали: ${result.notFound}, грешки: ${result.failed}`
            );
        } catch (error) {
            notify.error(error, "Цените не можаха да бъдат обновени");
        } finally {
            setRefreshing(false);
        }
    }

    async function handleUpdateCatalog() {
        try {
            await updateCatalog();
            notify.info("Каталозите се обновяват. Това отнема няколко минути.");
            setCatalog((current) => (current ? { ...current, updating: true } : current));
        } catch (error) {
            notify.error(error, "Обновяването на каталога не можа да започне");
        }
    }

    async function handleRefreshFilters() {
        try {
            await refreshCatalogFilters();
            notify.info("Филтрите се обновяват. Това отнема около минута.");
            setCatalog((current) => (current ? { ...current, updating: true } : current));
        } catch (error) {
            notify.error(error, "Обновяването на филтрите не можа да започне");
        }
    }

    const catalogByStore = new Map(catalog?.stores.map((entry) => [entry.storeCode, entry]) ?? []);

    return (
        <Box>
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 2, mb: 3, flexWrap: "wrap" }}>
                <Box>
                    <Typography component="h1" variant="h4">
                        Магазини
                    </Typography>
                    <Typography color="text.secondary">
                        Цените се обновяват автоматично на всеки 6 часа, а каталозите веднъж на ден.
                    </Typography>
                </Box>
                <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
                    <Button
                        variant="outlined"
                        startIcon={<FilterListIcon />}
                        onClick={handleRefreshFilters}
                        disabled={catalogUpdating}
                    >
                        Обнови филтрите
                    </Button>
                    <Button
                        variant="outlined"
                        startIcon={<CloudSyncIcon />}
                        onClick={handleUpdateCatalog}
                        disabled={catalogUpdating}
                    >
                        Обнови каталозите
                    </Button>
                    <Button variant="contained" startIcon={<RefreshIcon />} onClick={handleRefreshPrices} loading={refreshing}>
                        Обнови цените
                    </Button>
                </Box>
            </Box>

            {catalogUpdating && (
                <Card sx={{ mb: 3 }}>
                    <CardContent>
                        <Typography sx={{ mb: 1 }}>Каталозите или филтрите се обновяват...</Typography>
                        <LinearProgress />
                    </CardContent>
                </Card>
            )}

            <Card>
                <TableContainer>
                    <Table>
                        <TableHead>
                            <TableRow>
                                <TableCell>Магазин</TableCell>
                                <TableCell>Статус</TableCell>
                                <TableCell align="right">Продукти в каталога</TableCell>
                                <TableCell>Каталог обновен</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {stores.map((store) => {
                                const status = STATUS_LABELS[store.status];
                                const storeCatalog = catalogByStore.get(store.code);
                                return (
                                    <TableRow key={store.id}>
                                        <TableCell>
                                            <Typography sx={{ fontWeight: 700 }}>{store.name}</Typography>
                                            <Link href={store.baseUrl} target="_blank" rel="noopener noreferrer" variant="body2">
                                                {store.baseUrl.replace(/^https?:\/\//, "")}
                                            </Link>
                                        </TableCell>
                                        <TableCell>
                                            <Chip size="small" label={status.label} color={status.color} />
                                        </TableCell>
                                        <TableCell align="right">
                                            {storeCatalog ? formatNumber(storeCatalog.links) : (
                                                <Typography variant="body2" color="text.secondary">
                                                    още не се поддържа
                                                </Typography>
                                            )}
                                        </TableCell>
                                        <TableCell sx={{ color: "text.secondary" }}>
                                            {storeCatalog ? formatDateTime(storeCatalog.updatedAt) : "—"}
                                        </TableCell>
                                    </TableRow>
                                );
                            })}
                        </TableBody>
                    </Table>
                </TableContainer>
            </Card>
        </Box>
    );
}

export default StoresPage;
