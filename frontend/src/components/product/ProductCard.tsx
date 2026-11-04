import { Link } from "react-router-dom";

import { Box, Card, CardActionArea, CardContent, Typography } from "@mui/material";

import type { ProductSummary } from "../../types/api";
import { formatPrice } from "../../utils/format";
import ProductImage from "./ProductImage";

type Props = {
    product: ProductSummary;
};

function ProductCard({ product }: Props) {
    return (
        <Card sx={{ height: "100%", "&:hover": { borderColor: "primary.light" } }}>
            <CardActionArea
                component={Link}
                to={`/products/${product.id}`}
                sx={{ height: "100%", display: "flex", flexDirection: "column", alignItems: "stretch" }}
            >
                <ProductImage src={product.imageUrl} alt={product.name} height={160} />
                <CardContent sx={{ flexGrow: 1, display: "flex", flexDirection: "column", gap: 0.5 }}>
                    {product.brand && (
                        <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                            {product.brand}
                        </Typography>
                    )}
                    <Typography
                        sx={{
                            fontWeight: 600,
                            display: "-webkit-box",
                            WebkitLineClamp: 2,
                            WebkitBoxOrient: "vertical",
                            overflow: "hidden",
                        }}
                    >
                        {product.name}
                    </Typography>
                    <Box sx={{ mt: "auto", pt: 1 }}>
                        <Typography variant="h6" color="primary.main">
                            {product.lowestPrice != null ? `от ${formatPrice(product.lowestPrice)}` : "Няма наличност"}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                            в {product.offerCount} {product.offerCount === 1 ? "магазин" : "магазина"}
                        </Typography>
                    </Box>
                </CardContent>
            </CardActionArea>
        </Card>
    );
}

export default ProductCard;
