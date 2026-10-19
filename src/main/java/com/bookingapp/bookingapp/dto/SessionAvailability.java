package com.bookingapp.bookingapp.dto;

import com.bookingapp.bookingapp.entity.Session;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One cell of the availability grid: whether the requested group and gear fit
 * into this session on this date, and what the booking would cost.
 */
public record SessionAvailability(LocalDate date,
                                  Session session,
                                  boolean available,
                                  BigDecimal totalPrice) {
}