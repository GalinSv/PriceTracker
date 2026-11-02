package com.pricetracker.util;

import java.util.Locale;

public final class TextUtils {

    private TextUtils() {
    }

    // "DGS-1008D", "dgs 1008d" and "DGS1008D" all become "DGS1008D", so stores can be compared
    public static String normalizeModelCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalized.isEmpty() ? null : truncate(normalized, 100);
    }


    public static String normalizeBrand(String brand) {
        return brand == null ? "" : brand.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
