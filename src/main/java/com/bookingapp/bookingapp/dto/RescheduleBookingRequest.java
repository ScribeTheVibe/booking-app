package com.bookingapp.bookingapp.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Date only: session, group size and gear never change on a reschedule. */
public record RescheduleBookingRequest(@NotNull LocalDate newDate) {
}
