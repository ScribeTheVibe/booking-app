package com.bookingapp.bookingapp.exception;

public class InsufficientAvailabilityException extends RuntimeException {
    public InsufficientAvailabilityException(String message) {
        super(message);
    }
}
