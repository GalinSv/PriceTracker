import { Link } from "react-router-dom";

import { Box, Button, Typography } from "@mui/material";

function NotFoundPage() {
    return (
        <Box sx={{ textAlign: "center", py: 8 }}>
            <Typography component="h1" variant="h4" sx={{ mb: 1 }}>
                Страницата не е намерена
            </Typography>
            <Typography color="text.secondary" sx={{ mb: 3 }}>
                Провери адреса или се върни към търсенето.
            </Typography>
            <Button component={Link} to="/" variant="contained">
                Към търсенето
            </Button>
        </Box>
    );
}

export default NotFoundPage;
