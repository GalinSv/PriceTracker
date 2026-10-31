package com.pricetracker.exception;

// Thrown when something is not found by id/code. Will be turned into HTTP 404.
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
