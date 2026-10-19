package com.bookingapp.bookingapp.exception;

public class PhoneAlreadyInUseException extends RuntimeException{
    public PhoneAlreadyInUseException(String phone){
        super("Phone already in use: " + phone);
    }
}
