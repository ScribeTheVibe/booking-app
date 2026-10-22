package com.bookingapp.bookingapp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityRequest(
        @NotNull @Positive Integer numberOfPeople,
        @NotEmpty List<@Valid BookingItemRequest> items,
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate) {
}
