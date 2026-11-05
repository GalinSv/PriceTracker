import { createContext, useContext } from "react";

export type Auth = {
    // null when nobody is logged in
    username: string | null;
    isAdmin: boolean;
    login: () => void;
    register: () => void;
    logout: () => void;
};

export const AuthContext = createContext<Auth | null>(null);

export function useAuth() {
    const auth = useContext(AuthContext);
    if (!auth) {
        throw new Error("useAuth must be used inside AuthProvider");
    }
    return auth;
}
