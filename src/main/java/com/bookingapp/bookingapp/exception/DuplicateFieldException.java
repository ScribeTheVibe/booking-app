package com.bookingapp.bookingapp.exception;

import lombok.Getter;

@Getter
public class DuplicateFieldException extends RuntimeException {
    private final String field;
    public DuplicateFieldException(String field, String value){
        super(field + "already in use: " + value);
        this.field = field;
    }
}
