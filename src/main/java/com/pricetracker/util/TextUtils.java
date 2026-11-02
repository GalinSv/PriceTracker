package com.pricetracker.util;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TextUtils {

    // The official Bulgarian transliteration, the same that stores use in their links
    private static final Map<Character, String> CYRILLIC_TO_LATIN = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "g"),
            Map.entry('д', "d"), Map.entry('е', "e"), Map.entry('ж', "zh"), Map.entry('з', "z"),
            Map.entry('и', "i"), Map.entry('й', "y"), Map.entry('к', "k"), Map.entry('л', "l"),
            Map.entry('м', "m"), Map.entry('н', "n"), Map.entry('о', "o"), Map.entry('п', "p"),
            Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"), Map.entry('у', "u"),
            Map.entry('ф', "f"), Map.entry('х', "h"), Map.entry('ц', "ts"), Map.entry('ч', "ch"),
            Map.entry('ш', "sh"), Map.entry('щ', "sht"), Map.entry('ъ', "a"), Map.entry('ь', "y"),
            Map.entry('ю', "yu"), Map.entry('я', "ya"));

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

    // "Мишка Logitech G502-X" -> [mishka, logitech, g502, x]
    // Store links are in Latin letters, so Bulgarian words are transliterated: "мишка" -> "mishka"
    public static List<String> searchWords(String text) {
        if (text == null) {
            return List.of();
        }
        return Arrays.stream(transliterate(text.toLowerCase(Locale.ROOT)).split("[^a-z0-9]+"))
                .filter(word -> !word.isEmpty())
                .distinct()
                .toList();
    }

    public static String transliterate(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            result.append(CYRILLIC_TO_LATIN.getOrDefault(c, String.valueOf(c)));
        }
        return result.toString();
    }

    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
