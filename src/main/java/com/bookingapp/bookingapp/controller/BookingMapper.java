package com.bookingapp.bookingapp.controller;

import com.bookingapp.bookingapp.dto.BookingResponse;
import com.bookingapp.bookingapp.dto.BookingSummaryResponse;
import com.bookingapp.bookingapp.entity.BookableResource;
import com.bookingapp.bookingapp.entity.Booking;
import com.bookingapp.bookingapp.entity.BookingItem;
import com.bookingapp.bookingapp.entity.ResortTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

/**
 * Entity to response mapping. Every response is built against one fixed instant, so the
 * effective status (dropped / completed are derived) is consistent within a response.
 */
@Component
@RequiredArgsConstructor
public class BookingMapper {

    private final Clock clock;

    public BookingResponse toResponse(Booking booking) {
        Clock asOf = asOf();
        List<BookingResponse.Item> items = booking.getItems().stream().map(this::toItem).toList();
        BigDecimal total = items.stream()
                .map(BookingResponse.Item::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BookingResponse(
                booking.getId(),
                booking.getDate(),
                booking.getSession(),
                booking.getNumberOfPeople(),
                booking.effectiveStatus(asOf),
                total,
                items);
    }

    public List<BookingSummaryResponse> toSummaries(List<Booking> bookings) {
        Clock asOf = asOf();
        return bookings.stream()
                .map(b -> new BookingSummaryResponse(
                        b.getId(), b.getDate(), b.getSession(), b.getNumberOfPeople(),
                        b.effectiveStatus(asOf)))
                .toList();
    }

    private BookingResponse.Item toItem(BookingItem item) {
        BookableResource resource = item.getResource();
        return new BookingResponse.Item(
                resource.getId(),
                resource.getName(),
                resource.getType(),
                item.getQuantity(),
                item.getUnitPriceAtBooking(),
                item.subtotal());
    }

    private Clock asOf() {
        return Clock.fixed(clock.instant(), ResortTime.ZONE);
    }
}
