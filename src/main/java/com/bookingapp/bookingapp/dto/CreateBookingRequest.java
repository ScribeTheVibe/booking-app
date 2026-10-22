package com.bookingapp.bookingapp.dto;

import com.bookingapp.bookingapp.entity.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

/** No user id here on purpose: the caller comes from the authenticated principal. */
public record CreateBookingRequest(
        @NotNull LocalDate date,
        @NotNull Session session,
        @NotNull @Positive Integer numberOfPeople,
        @NotEmpty List<@Valid BookingItemRequest> items) {
}
