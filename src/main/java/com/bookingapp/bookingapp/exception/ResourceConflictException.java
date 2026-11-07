package com.bookingapp.bookingapp.exception;

public class ResourceConflictException extends RuntimeException {
    public ResourceConflictException(String message) { super(message); }
}