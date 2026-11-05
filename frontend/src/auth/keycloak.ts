import Keycloak from "keycloak-js";

import { KEYCLOAK_CLIENT_ID, KEYCLOAK_REALM, KEYCLOAK_URL } from "../config/auth";

export const keycloak = new Keycloak({ url: KEYCLOAK_URL, realm: KEYCLOAK_REALM, clientId: KEYCLOAK_CLIENT_ID });

let started: Promise<boolean> | null = null;

// Checks quietly, in a hidden frame, whether the user is already logged in to Keycloak.
// Started only once: React's strict mode runs effects twice, and keycloak-js can be started once.
export function startKeycloak() {
    started ??= keycloak.init({
        onLoad: "check-sso",
        pkceMethod: "S256",
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
        checkLoginIframe: false,
    });
    return started;
}

// A token valid for at least 30 more seconds, or none when nobody is logged in
export async function getToken() {
    if (!keycloak.authenticated) return undefined;
    try {
        await keycloak.updateToken(30);
        return keycloak.token;
    } catch {
        // The login has expired; the request goes out like one from a guest
        return undefined;
    }
}
