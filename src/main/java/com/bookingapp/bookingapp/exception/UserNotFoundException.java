package com.bookingapp.bookingapp.exception;

public class UserNotFoundException extends RuntimeException{
    public UserNotFoundException(String message) { super(message);}
}
