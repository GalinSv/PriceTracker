package com.pricetracker.service.impl;

import java.util.Collections;
import java.util.List;

// Tells the accessories of a searched product from the product itself, in SQL: the search sorts by it, so it has
// to be decided for every found link in the database.
final class AccessoryRule {

    // "za" and "for" ("kalaf za ...", "case for ...") and the words that name an accessory, as in the links:
    // калъф/калъфче, кейс, протектор, стъкло, фолио, каишка, стойка, зарядно, кабел
    // Stores write "ъ" in their links as "a", "y" or "u": kalaf, kalyf, kaluf.
    private static final List<String> WORDS = List.of("za", "for", "kalaf", "kalafche", "kal f", "kalyf",
            "kalyfche", "kaluf", "kalufche", "keys", "case", "cover", "bumper", "folio", "protektor", "protector",
            "staklo", "styklo", "stuklo", "glass", "film", "kaishka", "strap", "stoyka", "stand", "holder",
            "zaryadno", "charger", "kabel", "cable", "s pen", "stylus", "pisalka",
            // Samsung's codes of its own cases, pens, chargers and films: "EF-US948CTEGWW", "EJ-PS948BBEGWW"
            "e[fjpt] [a-z0-9]{6,}");
    // The same words as stores write them in a product's name; a link doesn't always say what the product is:
    // ".../samsung-galaxy-s26-ultra-prozrachen" is "Калъф за смартфон Samsung Galaxy S26 Ultra Прозрачен"
    private static final String NAME = "(калъф|кейс|протектор|стъклен|фолио|каишка|стойка|зарядно|кабел|гръб|(^| )за )";
    // A case or ear tips named after the product: "iphone 17 pro max clear case with magsafe". Not the case a
    // product comes with ("airpods pro 3 with magsafe case", "s kalaf za zarezhdane") and not the body of a watch
    // ("apple watch series 11 46mm aluminium case").
    private static final String CASE_AFTER_NAME = "(?<! with| s| sas| i| and| magsafe| charging| zaryaden"
            + "| zarezhdasht| aluminium| aluminum| titanium| steel| ceramic)"
            + " (case|cover|kalaf|kalafche|kalyf|kaluf|kal f|keys|bumper|folio|tips|tapi|tapichki) ";
    // Makers of cases and screen protectors only, as CatalogServiceImpl.spellingKey writes them. Their links often
    // have no accessory word at all: "Spigen Vault Apple AirPods Pro 3", "Ringke Onyx Magnetic ... AirPods Pro 3".
    private static final List<String> BRANDS = List.of("spigen", "ringke", "esr", "puro", "3mk",
            "techprotect", "uag", "urbanarmor", "otterbox", "nillkin", "duxducis", "hofi", "mobiwear", "nativeunion",
            "panzerglass", "wozinsky", "forcell", "tactical", "casemate", "pitaka", "elago", "caseology", "supcase",
            "araree", "itskins", "rhinoshield", "zagg", "speck", "dekoni");

    private AccessoryRule() {
    }

    // A boolean SQL expression over a catalog link "e" and its offer "o" (o.title may be null): true for an
    // accessory of what the words search for. Its values are added to params.
    //
    // Accessories are the links with "za", "for" or an accessory word before the first search word, like
    // "kalaf za samsung galaxy buds 4 pro" or "native union active case airpods pro 3". They have all the search
    // words too, often word for word. A word after the product ("airpods pro 3 with magsafe case") doesn't count,
    // and neither do words of the search itself: "kalaf iphone 17" looks for the cases.
    // Everything from a maker of cases is an accessory too, unless the search names that maker.
    static String sql(List<String> words, List<Object> params) {
        List<String> accessoryWords = WORDS.stream().filter(word -> !words.contains(word)).toList();
        params.add(" " + words.get(0));
        params.add(" (" + String.join("|", accessoryWords) + ") ");
        // The text up to the first search word; empty when the link doesn't have it at the start of a word
        String wordBefore = "left(' ' || e.search_text || ' ', strpos(' ' || e.search_text, ?)) ~ ?";
        if (accessoryWords.size() == WORDS.size()) {
            // The same in the name read from the product's page, up to the first search word
            String name = "lower(coalesce(o.title, e.title, ''))";
            wordBefore += " OR left(" + name + ", strpos(" + name + ", ?)) ~ ?";
            params.add(words.get(0));
            params.add(NAME);
            // And a case word after the first search word
            wordBefore += " OR (strpos(' ' || e.search_text, ?) > 0"
                    + " AND substr(' ' || e.search_text || ' ', strpos(' ' || e.search_text, ?) + 1) ~ ?)";
            params.add(" " + words.get(0));
            params.add(" " + words.get(0));
            params.add(CASE_AFTER_NAME);
        }

        String searched = String.join("", words);
        List<String> brands = BRANDS.stream().filter(brand -> !searched.contains(brand)).toList();
        if (brands.isEmpty()) {
            return "(" + wordBefore + ")";
        }
        params.addAll(brands);
        return "(" + wordBefore + " OR "
                + CatalogServiceImpl.SPELLING_KEY_SQL.formatted("coalesce(e.facet_brand, '')") + " IN ("
                + String.join(", ", Collections.nCopies(brands.size(), "?")) + "))";
    }
}
