package com.pricetracker.scraper;

public final class Availability {

    private Availability() {
    }

    // schema.org availability, e.g. "https://schema.org/InStock"; null when the page doesn't say
    public static Boolean isInStock(String availability) {
        if (availability == null) {
            return null;
        }
        return availability.endsWith("InStock")
                || availability.endsWith("LimitedAvailability")
                || availability.endsWith("OnlineOnly")
                || availability.endsWith("InStoreOnly");
    }
}
