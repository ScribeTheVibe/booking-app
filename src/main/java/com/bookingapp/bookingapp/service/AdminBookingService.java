package com.bookingapp.bookingapp.service;

import com.bookingapp.bookingapp.entity.Booking;
import com.bookingapp.bookingapp.entity.BookingStatus;
import com.bookingapp.bookingapp.entity.ResortTime;
import com.bookingapp.bookingapp.entity.User;
import com.bookingapp.bookingapp.repository.BookingRepository;
import com.bookingapp.bookingapp.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Admin-facing booking operations. Unlike {@link BookingService}, every read returns
 * bookings for ALL users, not just the caller. Admin actions (confirm, reject, promote)
 * are also handled here.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    // ---------- Reads ----------

    /** Return all bookings ordered by date descending, then id descending. */
    public List<Booking> getAllBookings() {
        return bookingRepository.findAllByOrderByDateDescIdDesc();
    }

    /**
     * Return all bookings filtered by their effective status.
     * Uses the same effective-status derivation as {@link BookingService#getMyBookingsByStatus}.
     */
    public List<Booking> getAllBookingsByStatus(BookingStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status is required");
        }
        Clock asOf = Clock.fixed(clock.instant(), ResortTime.ZONE);
        return bookingRepository.findAllByOrderByDateDescIdDesc().stream()
                .filter(booking -> booking.effectiveStatus(asOf) == status)
                .toList();
    }

    /** Find any booking by its id, regardless of owner. */
    public Booking getBookingById(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));
    }

    // ---------- Commands ----------

    /**
     * Confirm a pending booking. The booking must be in PENDING effective status
     * and its session must not have started yet.
     */
    @Transactional
    public Booking confirmBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));
        booking.confirm(clock);
        return booking;
    }

    /**
     * Reject a pending or confirmed booking.
     */
    @Transactional
    public Booking rejectBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));
        booking.reject(clock);
        return booking;
    }

    /**
     * Promote a user to admin. The user's {@code admin} flag is set to {@code true}.
     * @return the updated user
     */
    @Transactional
    public User promoteToAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        user.promoteToAdmin();
        return userRepository.save(user);
    }
}
