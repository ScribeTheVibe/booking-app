package com.bookingapp.bookingapp.exception;

public class InvalidUserCredentialException extends RuntimeException{
    public InvalidUserCredentialException(String message){ super(message); }
}
