package com.pricetracker.scraper;

public final class Availability {

    private Availability() {
    }

    // schema.org availability, e.g. "https://schema.org/InStock"; null when the page doesn't say.
    // In stock means it can be bought now. That includes PreOrder and BackOrder: BestPC marks so the products
    // it brings from abroad for each order in 10-13 days, the same as MallBG's "in stock" from a European warehouse.
    public static Boolean isInStock(String availability) {
        if (availability == null) {
            return null;
        }
        return availability.endsWith("InStock")
                || availability.endsWith("LimitedAvailability")
                || availability.endsWith("OnlineOnly")
                || availability.endsWith("InStoreOnly")
                || availability.endsWith("PreOrder")
                || availability.endsWith("BackOrder");
    }

    // The store doesn't have it but brings it for the order, in days or weeks
    public static boolean isOnOrder(String availability) {
        return availability != null && (availability.endsWith("PreOrder") || availability.endsWith("BackOrder"));
    }
}
