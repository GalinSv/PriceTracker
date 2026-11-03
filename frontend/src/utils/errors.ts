import axios from "axios";

export function extractErrorMessage(error: unknown, fallback: string): string {
    if (axios.isAxiosError(error)) {
        const data = error.response?.data as unknown;
        if (data && typeof data === "object") {
            const message = (data as Record<string, unknown>).message;
            if (typeof message === "string" && message.trim()) return message;
        }
        if (!error.response) return "Сървърът не отговаря. Пуснат ли е backend-ът?";
    }
    return fallback;
}
