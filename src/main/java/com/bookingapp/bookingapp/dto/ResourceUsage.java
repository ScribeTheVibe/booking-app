package com.bookingapp.bookingapp.dto;

import com.bookingapp.bookingapp.entity.BookingStatus;
import com.bookingapp.bookingapp.entity.Session;

import java.time.LocalDate;

/**
 * One aggregated row of the usage query: how many units of a resource are booked
 * on a date, for one session, in one status. The availability calculation sums these
 * per half-day, after dropping rows that no longer hold capacity.
 */
public record ResourceUsage(Long resourceId,
                            LocalDate date,
                            Session session,
                            BookingStatus status,
                            Long quantity) {
}