package com.bookingapp.bookingapp.entity;

/** Stored review decisions plus time-derived states exposed by Booking.effectiveStatus. */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    // Time-derived states; no scheduled database update is required.
    DROPPED,
    COMPLETED
}