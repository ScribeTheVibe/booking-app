package com.bookingapp.bookingapp.controller;

import java.security.Principal;

/** Created by trusted authentication code after resolving the customer account. */
public record BookingPrincipal(Long userId) implements Principal {

    public BookingPrincipal {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User id must be positive");
        }
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}