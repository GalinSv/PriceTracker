import { useState } from "react";
import { Link, Outlet, useLocation } from "react-router-dom";

import { AppBar, Badge, Box, Button, Container, Toolbar, Typography } from "@mui/material";
import AddLinkIcon from "@mui/icons-material/AddLink";
import LocalOfferIcon from "@mui/icons-material/LocalOffer";
import LoginIcon from "@mui/icons-material/Login";
import LogoutIcon from "@mui/icons-material/Logout";

import { useAuth } from "../../auth/AuthContext";
import { useFavorites } from "../../favorites/FavoritesContext";
import AddByLinkDialog from "../offer/AddByLinkDialog";

type Access = "everyone" | "user" | "admin";

const NAV_ITEMS: { to: string; label: string; access: Access }[] = [
    { to: "/", label: "Търсене", access: "everyone" },
    { to: "/favorites", label: "Любими", access: "user" },
    { to: "/stores", label: "Магазини", access: "admin" },
];

function MainLayout() {
    const location = useLocation();
    const [addOpen, setAddOpen] = useState(false);
    const auth = useAuth();
    const { newsCount } = useFavorites();

    function isActive(to: string) {
        return to === "/" ? location.pathname === "/" : location.pathname.startsWith(to);
    }

    function canSee(access: Access) {
        if (access === "admin") return auth.isAdmin;
        if (access === "user") return auth.username != null;
        return true;
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
                    {/* On a phone the menu doesn't fit next to the buttons, so it goes on a second row */}
                    <Toolbar
                        disableGutters
                        sx={{ gap: 1, minHeight: 64, flexWrap: { xs: "wrap", md: "nowrap" }, py: { xs: 1, md: 0 } }}
                    >
                        <Box
                            component={Link}
                            to="/"
                            sx={{
                                display: "flex",
                                alignItems: "center",
                                gap: 1,
                                mr: { xs: "auto", md: 2 },
                                textDecoration: "none",
                                color: "text.primary",
                            }}
                        >
                            <LocalOfferIcon sx={{ color: "primary.main" }} />
                            <Typography variant="h6" sx={{ fontWeight: 800 }}>
                                Price Tracker
                            </Typography>
                        </Box>

                        <Box
                            component="nav"
                            sx={{
                                display: "flex",
                                flexGrow: 1,
                                gap: 0.5,
                                order: { xs: 1, md: 0 },
                                width: { xs: "100%", md: "auto" },
                                overflowX: "auto",
                            }}
                        >
                            {NAV_ITEMS.filter((item) => canSee(item.access)).map((item) => (
                                <Button
                                    key={item.to}
                                    component={Link}
                                    to={item.to}
                                    sx={{
                                        color: isActive(item.to) ? "primary.main" : "text.secondary",
                                        bgcolor: isActive(item.to) ? "rgba(15,110,86,0.08)" : "transparent",
                                    }}
                                >
                                    {item.to === "/favorites" && newsCount > 0 ? (
                                        <Badge
                                            color="error"
                                            badgeContent={newsCount}
                                            sx={{ "& .MuiBadge-badge": { right: -10 } }}
                                        >
                                            {item.label}
                                        </Badge>
                                    ) : (
                                        item.label
                                    )}
                                </Button>
                            ))}
                        </Box>

                        <Button
                            variant="contained"
                            startIcon={<AddLinkIcon />}
                            onClick={() => setAddOpen(true)}
                            aria-label="Добави по линк"
                            // Only the icon on a phone
                            sx={{ minWidth: 0, "& .MuiButton-startIcon": { mr: { xs: 0, sm: 1 }, ml: { xs: 0, sm: -0.5 } } }}
                        >
                            <Box component="span" sx={{ display: { xs: "none", sm: "inline" } }}>
                                Добави по линк
                            </Box>
                        </Button>

                        {auth.username ? (
                            <>
                                <Typography
                                    variant="body2"
                                    sx={{ ml: 1, fontWeight: 600, display: { xs: "none", sm: "block" } }}
                                >
                                    {auth.username}
                                </Typography>
                                <Button startIcon={<LogoutIcon />} onClick={auth.logout}>
                                    Изход
                                </Button>
                            </>
                        ) : (
                            <>
                                <Button startIcon={<LoginIcon />} onClick={auth.login} sx={{ ml: 1 }}>
                                    Вход
                                </Button>
                                <Button onClick={auth.register} sx={{ display: { xs: "none", sm: "inline-flex" } }}>
                                    Регистрация
                                </Button>
                            </>
                        )}
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
