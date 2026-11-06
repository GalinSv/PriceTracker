import type { ReactNode } from "react";

import { Alert, Button } from "@mui/material";

import { useAuth } from "./AuthContext";

function RequireLogin({ children, message }: { children: ReactNode; message: string }) {
    const auth = useAuth();
    if (auth.username) {
        return children;
    }
    return (
        <Alert
            severity="info"
            action={
                <Button color="inherit" size="small" onClick={auth.login}>
                    Вход
                </Button>
            }
        >
            {message}
        </Alert>
    );
}

export default RequireLogin;
