import { toast } from "react-toastify";

import { extractErrorMessage } from "./errors";

export const notify = {
    success(message: string) {
        toast.success(message);
    },
    info(message: string) {
        toast.info(message);
    },
    error(error: unknown, fallback: string) {
        toast.error(extractErrorMessage(error, fallback));
    },
};
