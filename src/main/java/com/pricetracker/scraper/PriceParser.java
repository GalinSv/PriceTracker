package com.pricetracker.scraper;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Turns price text like "18.54", "50,62 €" or "1 299,00 лв." into a BigDecimal
public final class PriceParser {

    private PriceParser() {
    }

    public static BigDecimal parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.matches("\\d+(\\.\\d+)?")) {
            return new BigDecimal(trimmed).setScale(2, RoundingMode.HALF_UP);
        }

        String digits = trimmed.replaceAll("[^0-9.,]", "");
        if (digits.isEmpty()) {
            return null;
        }

        // The last "," or "." followed by 1-2 digits is the decimal separator; any other is a thousands separator
        int separator = Math.max(digits.lastIndexOf(','), digits.lastIndexOf('.'));
        String normalized;
        if (separator >= 0 && digits.length() - separator - 1 <= 2) {
            String whole = digits.substring(0, separator).replaceAll("[.,]", "");
            normalized = whole + "." + digits.substring(separator + 1);
        } else {
            normalized = digits.replaceAll("[.,]", "");
        }
        if (normalized.isEmpty() || normalized.equals(".")) {
            return null;
        }
        return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
    }
}
