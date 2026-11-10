import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import {
    Alert,
    Box,
    Button,
    Card,
    CardContent,
    Chip,
    LinearProgress,
    Typography,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import StorefrontIcon from "@mui/icons-material/Storefront";

import FavoriteButton from "../../components/favorites/FavoriteButton";
import OffersTable from "../../components/product/OffersTable";
import PriceHistoryChart from "../../components/product/PriceHistoryChart";
import ProductImage from "../../components/product/ProductImage";
import { getOfferHistory } from "../../services/offerService";
import { findNewStores, getProduct } from "../../services/productService";
import type { PriceSnapshot, ProductDetails } from "../../types/api";
import { extractErrorMessage } from "../../utils/errors";
import { formatPrice } from "../../utils/format";
import { notify } from "../../utils/notify";

function ProductPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const productId = Number(id);

    const [product, setProduct] = useState<ProductDetails | null>(null);
    const [historyByOffer, setHistoryByOffer] = useState<Record<number, PriceSnapshot[]>>({});
    const [error, setError] = useState<string | null>(null);
    const [searchingStores, setSearchingStores] = useState(false);

    // The catalog may have been scrolled far down
    useEffect(() => {
        window.scrollTo(0, 0);
    }, [productId]);

    useEffect(() => {
        let cancelled = false;
        getProduct(productId)
            .then(async (loaded) => {
                if (cancelled) return;
                setProduct(loaded);
                setError(null);
                const histories = await Promise.all(
                    loaded.offers.map(async (offer) => [offer.id, await getOfferHistory(offer.id)] as const)
                );
                if (!cancelled) setHistoryByOffer(Object.fromEntries(histories));
            })
            .catch((e) => !cancelled && setError(extractErrorMessage(e, "Продуктът не можа да бъде зареден")));
        return () => {
            cancelled = true;
        };
    }, [productId]);

    if (error) {
        return <Alert severity="error">{error}</Alert>;
    }
    if (!product || product.id !== productId) {
        return <LinearProgress />;
    }

    const storesInStock = product.offers.filter((offer) => offer.inStock).length;

    async function handleFindStores() {
        if (!product) return;
        setSearchingStores(true);
        try {
            const updated = await findNewStores(product.id);
            const added = updated.offers.length - product.offers.length;
            setProduct(updated);
            if (added <= 0) {
                notify.info("Няма нови магазини за този продукт");
                return;
            }
            notify.success(added === 1 ? "Добавен е 1 нов магазин" : `Добавени са ${added} нови магазина`);
            const newOffers = updated.offers.filter((offer) => !(offer.id in historyByOffer));
            const histories = await Promise.all(
                newOffers.map(async (offer) => [offer.id, await getOfferHistory(offer.id)] as const),
            );
            setHistoryByOffer((current) => ({ ...current, ...Object.fromEntries(histories) }));
        } catch (e) {
            notify.error(e, "Търсенето на нови магазини не успя");
        } finally {
            setSearchingStores(false);
        }
    }

    return (
        <Box>
            <Button startIcon={<ArrowBackIcon />} onClick={() => navigate(-1)} sx={{ mb: 2 }}>
                Назад
            </Button>

            <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "320px 1fr" }, gap: 3, mb: 3 }}>
                <Card>
                    <ProductImage src={product.imageUrl} alt={product.name} height={280} />
                </Card>

                <Box>
                    {product.brand && (
                        <Typography color="text.secondary" sx={{ fontWeight: 600 }}>
                            {product.brand}
                        </Typography>
                    )}
                    <Typography component="h1" variant="h4" sx={{ mb: 2 }}>
                        {product.name}
                    </Typography>

                    <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap", mb: 3 }}>
                        {product.modelCode && <Chip label={`Модел: ${product.modelCode}`} variant="outlined" />}
                        {product.ean && <Chip label={`EAN: ${product.ean}`} variant="outlined" />}
                        {product.categoryName && <Chip label={product.categoryName} variant="outlined" />}
                    </Box>

                    <Typography color="text.secondary">Най-ниска цена</Typography>
                    <Typography variant="h4" color="primary.main">
                        {formatPrice(product.lowestPrice)}
                    </Typography>
                    <Typography color="text.secondary">
                        в наличност в {storesInStock} от {product.offers.length}{" "}
                        {product.offers.length === 1 ? "магазин" : "магазина"}
                    </Typography>
                    <Box sx={{ mt: 2 }}>
                        <FavoriteButton productId={product.id} withLabel />
                    </Box>
                </Box>
            </Box>

            <Card sx={{ mb: 3 }}>
                <CardContent>
                    <Box
                        sx={{
                            display: "flex",
                            justifyContent: "space-between",
                            alignItems: "center",
                            gap: 2,
                            flexWrap: "wrap",
                            mb: 1,
                        }}
                    >
                        <Typography component="h2" variant="h6">
                            Цени по магазини
                        </Typography>
                        <Button
                            variant="outlined"
                            size="small"
                            startIcon={<StorefrontIcon />}
                            onClick={handleFindStores}
                            loading={searchingStores}
                        >
                            Търси нови магазини
                        </Button>
                    </Box>
                    <OffersTable offers={product.offers} lowestPrice={product.lowestPrice} />
                </CardContent>
            </Card>

            <Card>
                <CardContent>
                    <Typography component="h2" variant="h6" sx={{ mb: 2 }}>
                        История на цената
                    </Typography>
                    <PriceHistoryChart offers={product.offers} historyByOffer={historyByOffer} />
                </CardContent>
            </Card>
        </Box>
    );
}

export default ProductPage;
