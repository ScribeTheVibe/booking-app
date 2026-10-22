package com.bookingapp.bookingapp.controller;

import com.bookingapp.bookingapp.dto.AvailabilityRequest;
import com.bookingapp.bookingapp.dto.BookingResponse;
import com.bookingapp.bookingapp.dto.BookingSummaryResponse;
import com.bookingapp.bookingapp.dto.CreateBookingRequest;
import com.bookingapp.bookingapp.dto.RescheduleBookingRequest;
import com.bookingapp.bookingapp.dto.SessionAvailability;
import com.bookingapp.bookingapp.entity.Booking;
import com.bookingapp.bookingapp.entity.BookingStatus;
import com.bookingapp.bookingapp.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Customer-facing booking API. There is no cancel endpoint by design, and admin
 * operations will live in a separate controller.
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final BookingMapper mapper;
    private final CurrentUserProvider currentUser;

    /** My bookings, newest first. Optional filter by customer-visible status. */
    @GetMapping
    public List<BookingSummaryResponse> listMine(@RequestParam(required = false) BookingStatus status) {
        Long userId = currentUser.requireUserId();
        List<Booking> bookings = (status == null)
                ? bookingService.getMyBookings(userId)
                : bookingService.getMyBookingsByStatus(userId, status);
        return mapper.toSummaries(bookings);
    }

    @GetMapping("/{id}")
    public BookingResponse getMine(@PathVariable Long id) {
        return mapper.toResponse(bookingService.getMyBooking(id, currentUser.requireUserId()));
    }

    /**
     * The browsing grid for a group size and gear. POST because the item list is a body,
     * but it is a safe read: nothing is created or locked.
     */
    @PostMapping("/availability")
    public List<SessionAvailability> availability(@Valid @RequestBody AvailabilityRequest request) {
        return bookingService.findAvailability(
                request.numberOfPeople(), request.items(), request.fromDate(), request.toDate());
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request) {
        Booking booking = bookingService.createBooking(
                currentUser.requireUserId(),
                request.date(), request.session(), request.numberOfPeople(), request.items());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(booking.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(booking));
    }

    /** Moves the booking to another date. Everything else about it stays the same. */
    @PostMapping("/{id}/reschedule")
    public BookingResponse reschedule(@PathVariable Long id,
                                      @Valid @RequestBody RescheduleBookingRequest request) {
        return mapper.toResponse(
                bookingService.rescheduleMyBooking(id, currentUser.requireUserId(), request.newDate()));
    }
}