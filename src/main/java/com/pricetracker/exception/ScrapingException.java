package com.pricetracker.exception;

// A store page could not be downloaded or read
public class ScrapingException extends RuntimeException {

    public ScrapingException(String message) {
        super(message);
    }

    public ScrapingException(String message, Throwable cause) {
        super(message, cause);
    }
}
