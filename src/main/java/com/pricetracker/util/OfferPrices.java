package com.pricetracker.util;

import com.pricetracker.entity.Offer;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;

public final class OfferPrices {

    private OfferPrices() {
    }

    // The product's price is the lowest one among the stores that have it in stock
    public static BigDecimal lowestInStock(Collection<Offer> offers) {
        return offers.stream()
                .filter(offer -> Boolean.TRUE.equals(offer.getInStock()))
                .map(Offer::getCurrentPrice)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }
}
