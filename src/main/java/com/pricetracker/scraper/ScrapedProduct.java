package com.pricetracker.scraper;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// What we read from one product page in one store
@Data
@NoArgsConstructor
public class ScrapedProduct {

    private static final Pattern EAN = Pattern.compile("(?<!\\d)\\d{8,14}(?!\\d)");
    private static final Pattern BARCODES_ONLY = Pattern.compile("\\d{8,14}(\\s*,\\s*\\d{8,14})*");

    private String url;
    private String storeCode;
    private String title;
    private String brand;
    private String modelCode;
    // A store can list several barcodes for one product, e.g. one per region
    private List<String> eans = new ArrayList<>();
    private String storeSku;
    private BigDecimal price;
    private String currency;
    private Boolean inStock;
    // In stock, but the store first has to bring it: shown as "По поръчка"
    private boolean onOrder;
    private String imageUrl;

    // "195950639049, 195950639094" -> both barcodes
    public void addEans(String text) {
        if (text == null) {
            return;
        }
        Matcher matcher = EAN.matcher(text);
        while (matcher.find()) {
            if (!eans.contains(matcher.group())) {
                eans.add(matcher.group());
            }
        }
    }

    // Some stores put the barcode where the model code should be, e.g. "8806095597263"
    public void moveBarcodesOutOfModelCode() {
        if (modelCode != null && BARCODES_ONLY.matcher(modelCode.trim()).matches()) {
            addEans(modelCode);
            modelCode = null;
        }
    }
}
