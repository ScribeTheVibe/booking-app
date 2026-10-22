package com.bookingapp.bookingapp.dto;

import com.bookingapp.bookingapp.entity.BookingStatus;
import com.bookingapp.bookingapp.entity.Session;

import java.time.LocalDate;

/** List view. Deliberately carries no items, so listing never touches lazy collections. */
public record BookingSummaryResponse(
        Long id,
        LocalDate date,
        Session session,
        int numberOfPeople,
        BookingStatus status) {
}
