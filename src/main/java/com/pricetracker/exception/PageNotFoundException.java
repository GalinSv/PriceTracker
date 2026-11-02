package com.pricetracker.exception;

// The store returned 404/410: the product page no longer exists
public class PageNotFoundException extends ScrapingException {

    public PageNotFoundException(String message) {
        super(message);
    }
}
