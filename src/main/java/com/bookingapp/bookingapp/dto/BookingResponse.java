package com.bookingapp.bookingapp.dto;

import com.bookingapp.bookingapp.entity.BookingStatus;
import com.bookingapp.bookingapp.entity.ResourceType;
import com.bookingapp.bookingapp.entity.Session;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Detail view. {@code status} is the customer-visible (effective) status, not the stored one. */
public record BookingResponse(
        Long id,
        LocalDate date,
        Session session,
        int numberOfPeople,
        BookingStatus status,
        BigDecimal totalPrice,
        List<Item> items) {

    public record Item(
            Long resourceId,
            String name,
            ResourceType type,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {
    }
}
