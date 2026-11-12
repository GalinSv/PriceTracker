package com.pricetracker.exception;

// The page is there but shows a price of 0: stores do this with products they no longer sell
public class NoPriceException extends ScrapingException {

    public NoPriceException(String message) {
        super(message);
    }
}
