export function formatPrice(value: number | null | undefined, currency = "EUR") {
    if (value == null) return "—";
    return new Intl.NumberFormat("bg-BG", { style: "currency", currency }).format(value);
}

export function formatDateTime(value: string | number | null | undefined) {
    if (value == null) return "—";
    return new Date(value).toLocaleString("bg-BG", { dateStyle: "short", timeStyle: "short" });
}

export function formatDate(value: string | number) {
    return new Date(value).toLocaleDateString("bg-BG", { day: "2-digit", month: "2-digit" });
}

export function formatNumber(value: number) {
    return new Intl.NumberFormat("bg-BG").format(value);
}
