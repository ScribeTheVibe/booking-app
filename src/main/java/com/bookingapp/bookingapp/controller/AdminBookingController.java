package com.bookingapp.bookingapp.controller;

import com.bookingapp.bookingapp.dto.BookingResponse;
import com.bookingapp.bookingapp.dto.BookingSummaryResponse;
import com.bookingapp.bookingapp.entity.Booking;
import com.bookingapp.bookingapp.entity.BookingStatus;
import com.bookingapp.bookingapp.service.AdminBookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-facing booking API. Unlike {@link BookingController}, every read returns
 * bookings for ALL users, not just the caller. Admin actions (confirm, reject, promote)
 * are also handled here.
 * <p>
 * Endpoints are under {@code /api/admin/bookings} and protected by the security
 * configuration's {@code hasRole("ADMIN")} rule.
 */
@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
public class AdminBookingController {

    private final AdminBookingService adminBookingService;
    private final BookingMapper mapper;

    // ---------- Reads ----------

    /** Return all bookings, newest first. Optional filter by effective status. */
    @GetMapping
    public List<BookingSummaryResponse> listAll(@RequestParam(required = false) BookingStatus status) {
        List<Booking> bookings = (status == null)
                ? adminBookingService.getAllBookings()
                : adminBookingService.getAllBookingsByStatus(status);
        return mapper.toSummaries(bookings);
    }

    /** Return a single booking by id, regardless of owner. */
    @GetMapping("/{id}")
    public BookingResponse getById(@PathVariable Long id) {
        return mapper.toResponse(adminBookingService.getBookingById(id));
    }

    // ---------- Commands ----------

    /** Confirm a pending booking for any user. */
    @PostMapping("/{id}/confirm")
    public BookingResponse confirm(@PathVariable Long id) {
        return mapper.toResponse(adminBookingService.confirmBooking(id));
    }

    /** Reject a pending or confirmed booking for any user. */
    @PostMapping("/{id}/reject")
    public BookingResponse reject(@PathVariable Long id) {
        return mapper.toResponse(adminBookingService.rejectBooking(id));
    }


}
