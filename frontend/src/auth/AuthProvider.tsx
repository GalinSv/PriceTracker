import { useEffect, useState } from "react";
import type { ReactNode } from "react";

import { LinearProgress } from "@mui/material";

import { AuthContext } from "./AuthContext";
import type { Auth } from "./AuthContext";
import { keycloak, startKeycloak } from "./keycloak";

// Waits for the quiet login check, then tells the whole app who is logged in
function AuthProvider({ children }: { children: ReactNode }) {
    const [ready, setReady] = useState(false);

    useEffect(() => {
        startKeycloak()
            // Keycloak is down: the site still works, only without logging in
            .catch(() => false)
            .finally(() => setReady(true));
    }, []);

    if (!ready) {
        return <LinearProgress />;
    }

    const auth: Auth = {
        username: keycloak.authenticated ? (keycloak.tokenParsed?.preferred_username ?? null) : null,
        isAdmin: keycloak.hasRealmRole("admin"),
        login: () => void keycloak.login(),
        register: () => void keycloak.register(),
        logout: () => void keycloak.logout({ redirectUri: window.location.origin }),
    };
    return <AuthContext.Provider value={auth}>{children}</AuthContext.Provider>;
}

export default AuthProvider;
