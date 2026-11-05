import type { ReactNode } from "react";

import { Alert, Button } from "@mui/material";

import { useAuth } from "./AuthContext";

// Shows the page only to admins; the backend checks the role again on every request
function AdminOnly({ children }: { children: ReactNode }) {
    const auth = useAuth();
    if (auth.isAdmin) {
        return children;
    }
    return (
        <Alert
            severity="info"
            action={
                !auth.username && (
                    <Button color="inherit" size="small" onClick={auth.login}>
                        Вход
                    </Button>
                )
            }
        >
            Тази страница е само за администратори.
        </Alert>
    );
}

export default AdminOnly;
