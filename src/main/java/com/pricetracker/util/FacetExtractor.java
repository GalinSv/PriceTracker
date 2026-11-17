package com.pricetracker.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Reads the filter values of a catalog link from its search words, e.g.
// It only guesses from the name, so it is right most of the time, not always.
public final class FacetExtractor {

    public record Facets(String brand, String series, String memory, String color) {
    }

    // The words after these name the device an accessory is for: "калъф за Samsung"
    private static final Set<String> FOR_WORDS = Set.of("za", "for");
    private static final int MAX_BRAND_WORDS = 3;
    private static final int MAX_SERIES_WORDS = 5;
    private static final int MIN_MODEL_CODE_LENGTH = 6;
    // "pro", "max": they belong to the series name, "Galaxy S26 Ultra", "iPhone 17 Pro Max".
    // Also the lines inside a line: "Galaxy Z Fold 7", "Galaxy Tab S10 FE", "Redmi Note 14 Pro", "Galaxy Buds 4".
    private static final Set<String> SERIES_WORDS = Set.of("pro", "max", "ultra", "plus", "mini", "lite", "x", "se",
            "fe", "air", "neo", "edge", "slim", "fold", "flip", "s", "e", "z", "tab", "note", "buds", "watch", "xl");
    // Words that are a product type, not a series: "Logitech mouse", "JBL bluetooth speaker"
    private static final Set<String> NOT_SERIES = Set.of("mishka", "klaviatura", "slushalki", "kalaf", "kabel",
            "zaryadno", "tonkolona", "kolonka", "laptop", "telefon", "smartfon", "monitor", "televizor", "printer",
            "adapter", "hab", "hub", "kamera", "mouse", "keyboard", "headset", "headphones", "speaker", "case",
            "cable", "charger", "bluetooth", "wireless", "gaming", "geymarska", "geymarski", "geyming", "portativna",
            "portativen", "bezzhichna", "bezzhichen", "bezzhichni", "html", "za", "for", "s", "i", "and", "with");
    // 256gb, 1tb, also "512 gb"
    private static final Pattern CAPACITY = Pattern.compile("(\\d{1,4})(gb|tb)");
    // RAM and storage glued in a link: "8256gb" is 8/256 GB, "161tb" is 16 GB/1 TB
    private static final Pattern RAM_AND_CAPACITY =
            Pattern.compile("(?:4|6|8|12|16|24)((?:64|128|256|512)gb)|(?:8|12|16|24)([12]tb)");
    // 5g, 144hz, 35k, 65w: sizes and units end a series name
    private static final Pattern NUMBER_WITH_UNIT = Pattern.compile("\\d+[a-z]{1,3}");
    // "s948", "a566b", "ga403": the maker's code of a model, which some stores write after or before its name
    private static final Pattern SHORT_MODEL_CODE = Pattern.compile("[a-z]{1,2}\\d{3,}[a-z]*");
    // Samsung's codes start with it: "Samsung SM-S948B Galaxy S26 Ultra"
    private static final Set<String> MODEL_CODE_PREFIXES = Set.of("sm");
    private static final Set<String> CAPACITY_UNITS = Set.of("gb", "tb");
    // In GB. Not 11 or 13: "iPad Air 11 256GB" names the 11 inch one.
    private static final Set<String> RAM_SIZES = Set.of("2", "3", "4", "6", "8", "12", "16", "18", "24", "32", "48",
            "64");
    // "flip7", "rtx4060": a word glued to its number
    private static final Pattern GLUED_NUMBER = Pattern.compile("([a-z]{3,})(\\d+)");
    private static final Map<String, String> COLORS = colors();

    // "tp link" -> "TP-Link": brand words as they appear in the search words -> name to show
    private final Map<String, String> brands;

    public FacetExtractor(Map<String, String> brands) {
        this.brands = brands;
    }

    // The brand list from resources/brands.txt. Brands seen on product pages only fix the spelling:
    // "UGREEN" from the list becomes "Ugreen", but "LOGITECH" in capitals does not replace "Logitech".
    public static FacetExtractor load(Collection<String> brandsFromPages) {
        Map<String, String> brands = new HashMap<>();
        for (String slug : readBrandList()) {
            brands.put(key(slug), displayName(slug));
        }
        for (String brand : brandsFromPages) {
            String name = brand.trim();
            String key = key(name);
            boolean shouted = name.length() > 3 && name.equals(name.toUpperCase(Locale.ROOT));
            if (brands.containsKey(key) && !shouted) {
                brands.put(key, name);
            }
        }
        return new FacetExtractor(brands);
    }

    public Facets extract(String searchText) {
        if (searchText == null || searchText.isBlank()) {
            return new Facets(null, null, null, null);
        }
        String[] words = searchText.split(" ");
        List<String> wordList = Arrays.asList(words);
        String brand = null;
        String series = null;
        for (int i = 0; i < words.length && brand == null; i++) {
            if (i > 0 && FOR_WORDS.contains(words[i - 1])) {
                continue;
            }
            // The longest brand first: "tech protect" before "tech"
            for (int length = Math.min(MAX_BRAND_WORDS, words.length - i); length >= 1; length--) {
                String name = brands.get(String.join(" ", wordList.subList(i, i + length)));
                if (name != null) {
                    brand = name;
                    series = series(name, words, i + length);
                    break;
                }
            }
        }
        return new Facets(brand, series, capacity(words), color(words));
    }

    // The words after the brand: the line name, then numbers and words like "pro": "Galaxy S26 Ultra"
    private String series(String brand, String[] words, int from) {
        List<String> series = new ArrayList<>();
        // The name already has its number: "s26" of "galaxy s26 ultra"
        boolean numbered = false;
        for (int i = afterModelCode(words, from); i < words.length && series.size() < MAX_SERIES_WORDS; i++) {
            String word = words[i];
            if (endsSeries(word) || isSizeNumber(words, i, numbered)) {
                break;
            }
            // "galaxy s26 ultra s948": the code after the name. "g502" alone is the name itself.
            if (numbered && SHORT_MODEL_CODE.matcher(word).matches()) {
                break;
            }
            boolean hasDigit = word.chars().anyMatch(Character::isDigit);
            if (series.isEmpty() || hasDigit || SERIES_WORDS.contains(word)) {
                series.add(word);
                numbered |= hasDigit;
            } else {
                break;
            }
        }
        if (series.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String word : series) {
            // "flip7" is the same series as "flip 7"; short ones like "g502" stay as they are
            Matcher glued = GLUED_NUMBER.matcher(word);
            if (glued.matches()) {
                parts.add(capitalize(glued.group(1)));
                parts.add(glued.group(2));
            } else {
                parts.add(capitalize(word));
            }
        }
        return TextUtils.truncate(brand + " " + String.join(" ", parts), 150);
    }

    // "sm s948b galaxy s26 ultra": the name starts after the code
    private int afterModelCode(String[] words, int from) {
        boolean codeFirst = from + 2 < words.length && MODEL_CODE_PREFIXES.contains(words[from])
                && SHORT_MODEL_CODE.matcher(words[from + 1]).matches()
                && words[from + 2].chars().allMatch(Character::isLetter);
        return codeFirst ? from + 2 : from;
    }

    // A number that is a size, not a part of the name: the "12" of "12 gb", and after the name's own number
    // also the RAM of "s26 ultra 12 256gb" (stores write "12/256GB"). The "17" of "iphone 17 256gb" stays.
    private boolean isSizeNumber(String[] words, int i, boolean numbered) {
        if (!words[i].chars().allMatch(Character::isDigit) || i + 1 >= words.length) {
            return false;
        }
        String next = words[i + 1];
        if (CAPACITY_UNITS.contains(next)) {
            return true;
        }
        boolean beforeSize = CAPACITY.matcher(next).matches()
                || (next.chars().allMatch(Character::isDigit) && i + 2 < words.length
                && CAPACITY_UNITS.contains(words[i + 2]));
        return numbered && beforeSize && RAM_SIZES.contains(words[i]);
    }

    private boolean endsSeries(String word) {
        boolean digitsOnly = word.chars().allMatch(Character::isDigit);
        boolean hasDigit = word.chars().anyMatch(Character::isDigit);
        return NOT_SERIES.contains(word) || COLORS.containsKey(word)
                || (digitsOnly && word.length() >= 3)               // part numbers and store ids: "910 006138"
                || (hasDigit && word.length() >= MIN_MODEL_CODE_LENGTH) // model codes: "fx607vjr"
                || (!digitsOnly && NUMBER_WITH_UNIT.matcher(word).matches());
    }

    // The largest one: a phone with "12gb 256gb" is a 256 GB phone
    private String capacity(String[] words) {
        long best = 0;
        String bestText = null;
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (i + 1 < words.length && word.chars().allMatch(Character::isDigit)
                    && (words[i + 1].equals("gb") || words[i + 1].equals("tb"))) {
                word = word + words[i + 1];
            }
            Matcher glued = RAM_AND_CAPACITY.matcher(word);
            if (glued.matches()) {
                word = glued.group(1) != null ? glued.group(1) : glued.group(2);
            }
            Matcher matcher = CAPACITY.matcher(word);
            if (!matcher.matches()) {
                continue;
            }
            long number = Long.parseLong(matcher.group(1));
            long gigabytes = matcher.group(2).equals("tb") ? number * 1024 : number;
            if (number > 0 && gigabytes > best) {
                best = gigabytes;
                bestText = number + " " + matcher.group(2).toUpperCase(Locale.ROOT);
            }
        }
        return bestText;
    }

    private String color(String[] words) {
        for (String word : words) {
            String color = COLORS.get(word);
            if (color != null) {
                return color;
            }
        }
        return null;
    }

    // "TP-Link", "Tech Protect" and "tech-protect" all become "tech protect", like the search words
    private static String key(String brand) {
        return String.join(" ", TextUtils.searchWords(brand));
    }

    // "tp-link" -> "TP-Link", "jbl" -> "JBL", "logitech" -> "Logitech"
    private static String displayName(String slug) {
        List<String> parts = new ArrayList<>();
        for (String part : slug.split("-")) {
            parts.add(part.length() <= 3 ? part.toUpperCase(Locale.ROOT) : capitalize(part));
        }
        return String.join("-", parts);
    }

    private static String capitalize(String word) {
        if (word.length() <= 2 || word.chars().anyMatch(Character::isDigit)) {
            return word.toUpperCase(Locale.ROOT);
        }
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    private static List<String> readBrandList() {
        try (InputStream in = FacetExtractor.class.getResourceAsStream("/brands.txt")) {
            if (in == null) {
                return List.of();
            }
            return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // The search words are transliterated, so "черен" is "cheren"; stores write "бял" as "byal" or "bjal"
    private static Map<String, String> colors() {
        Map<String, String> colors = new HashMap<>();
        Map<String, String> forms = Map.ofEntries(
                Map.entry("Черен", "cheren cherna cherno cherni black"),
                Map.entry("Бял", "byal byala byalo beli bjal bjala bjalo white"),
                Map.entry("Сив", "siv siva sivo sivi gray grey"),
                Map.entry("Сребрист", "srebrist srebrista srebristo silver"),
                Map.entry("Златист", "zlatist zlatista zlatisto gold"),
                Map.entry("Син", "sin sinya sinyo sini blue navy"),
                Map.entry("Червен", "cherven chervena cherveno cherveni red"),
                Map.entry("Зелен", "zelen zelena zeleno zeleni green"),
                Map.entry("Жълт", "zhalt zhalta zhalto yellow"),
                Map.entry("Оранжев", "oranzhev oranzheva oranzhevo orange"),
                Map.entry("Розов", "rozov rozova rozovo pink"),
                Map.entry("Лилав", "lilav lilava lilavo purple violet"),
                Map.entry("Кафяв", "kafyav kafyava kafyavo brown"),
                Map.entry("Бежов", "bezhov bezhova bezhovo beige"));
        forms.forEach((color, words) -> {
            for (String word : words.split(" ")) {
                colors.put(word, color);
            }
        });
        return colors;
    }
}
