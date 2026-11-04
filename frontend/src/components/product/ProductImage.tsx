import { useState } from "react";

import { Box } from "@mui/material";
import ImageNotSupportedIcon from "@mui/icons-material/ImageNotSupported";

type Props = {
    src: string | null;
    alt: string;
    height: number;
};

function ProductImage({ src, alt, height }: Props) {
    const [broken, setBroken] = useState(false);

    return (
        <Box
            sx={{
                height,
                bgcolor: "#fff",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                p: 1.5,
            }}
        >
            {src && !broken ? (
                <Box
                    component="img"
                    src={src}
                    alt={alt}
                    referrerPolicy="no-referrer"
                    onError={() => setBroken(true)}
                    sx={{ maxWidth: "100%", maxHeight: "100%", objectFit: "contain" }}
                />
            ) : (
                <ImageNotSupportedIcon sx={{ fontSize: 48, color: "divider" }} />
            )}
        </Box>
    );
}

export default ProductImage;
