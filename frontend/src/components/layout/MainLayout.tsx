import { useState } from "react";
import { Link, Outlet, useLocation } from "react-router-dom";

import { AppBar, Box, Button, Container, Toolbar, Typography } from "@mui/material";
import AddLinkIcon from "@mui/icons-material/AddLink";
import LocalOfferIcon from "@mui/icons-material/LocalOffer";

import AddByLinkDialog from "../offer/AddByLinkDialog";

const NAV_ITEMS = [
    { to: "/", label: "Търсене" },
    { to: "/stores", label: "Магазини" },
];

function MainLayout() {
    const location = useLocation();
    const [addOpen, setAddOpen] = useState(false);

    function isActive(to: string) {
        return to === "/" ? location.pathname === "/" : location.pathname.startsWith(to);
    }

    return (
        <Box sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
            <AppBar
                position="sticky"
                elevation={0}
                color="inherit"
                sx={{
                    bgcolor: "rgba(255,255,255,0.85)",
                    backdropFilter: "blur(8px)",
                    borderBottom: "1px solid",
                    borderColor: "divider",
                }}
            >
                <Container maxWidth="lg">
                    <Toolbar disableGutters sx={{ gap: 1, minHeight: 64 }}>
                        <Box
                            component={Link}
                            to="/"
                            sx={{
                                display: "flex",
                                alignItems: "center",
                                gap: 1,
                                mr: 2,
                                textDecoration: "none",
                                color: "text.primary",
                            }}
                        >
                            <LocalOfferIcon sx={{ color: "primary.main" }} />
                            <Typography variant="h6" sx={{ fontWeight: 800 }}>
                                Price Tracker
                            </Typography>
                        </Box>

                        <Box component="nav" sx={{ display: "flex", flexGrow: 1, gap: 0.5 }}>
                            {NAV_ITEMS.map((item) => (
                                <Button
                                    key={item.to}
                                    component={Link}
                                    to={item.to}
                                    sx={{
                                        color: isActive(item.to) ? "primary.main" : "text.secondary",
                                        bgcolor: isActive(item.to) ? "rgba(15,110,86,0.08)" : "transparent",
                                    }}
                                >
                                    {item.label}
                                </Button>
                            ))}
                        </Box>

                        <Button
                            variant="contained"
                            startIcon={<AddLinkIcon />}
                            onClick={() => setAddOpen(true)}
                        >
                            Добави по линк
                        </Button>
                    </Toolbar>
                </Container>
            </AppBar>

            <Container maxWidth="lg" sx={{ py: 4 }}>
                <Outlet />
            </Container>

            <AddByLinkDialog open={addOpen} onClose={() => setAddOpen(false)} />
        </Box>
    );
}

export default MainLayout;
