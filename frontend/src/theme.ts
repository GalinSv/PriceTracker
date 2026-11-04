import { createTheme } from "@mui/material/styles";

const theme = createTheme({
    palette: {
        mode: "light",
        primary: { main: "#0f6e56", dark: "#085041", light: "#1d9e75" },
        secondary: { main: "#d85a30" },
        success: { main: "#2e7d32" },
        background: { default: "#f3f6f5", paper: "#ffffff" },
        text: { primary: "#0f172a", secondary: "#52617a" },
        divider: "#e2e8f0",
    },
    shape: { borderRadius: 12 },
    typography: {
        fontFamily: '"Inter","Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif',
        h4: { fontWeight: 800, letterSpacing: "-0.5px" },
        h5: { fontWeight: 700 },
        h6: { fontWeight: 700 },
        button: { fontWeight: 600, textTransform: "none" },
    },
    components: {
        MuiCard: {
            defaultProps: { variant: "outlined" },
            styleOverrides: {
                root: {
                    borderColor: "#e2e8f0",
                    transition: "box-shadow .2s ease, border-color .2s ease",
                },
            },
        },
        MuiButton: {
            defaultProps: { disableElevation: true },
            styleOverrides: { root: { borderRadius: 10 } },
        },
        MuiChip: {
            styleOverrides: { root: { fontWeight: 600 } },
        },
        MuiTableHead: {
            styleOverrides: {
                root: {
                    "& th": {
                        fontWeight: 700,
                        color: "#334155",
                        backgroundColor: "#f8fafc",
                        whiteSpace: "nowrap",
                    },
                },
            },
        },
        MuiTableCell: {
            styleOverrides: { root: { borderColor: "#e2e8f0" } },
        },
    },
});

export default theme;
