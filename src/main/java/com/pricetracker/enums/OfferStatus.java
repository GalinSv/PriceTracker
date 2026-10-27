package com.pricetracker.enums;

public enum OfferStatus {
    ACTIVE,     // last check was successful
    NOT_FOUND,  // the page no longer exists (404)
    ERROR       // the last check failed
}
