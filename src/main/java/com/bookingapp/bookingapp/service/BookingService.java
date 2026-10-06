package com.bookingapp.bookingapp.service;

import com.bookingapp.bookingapp.dto.BookingItemRequest;
import com.bookingapp.bookingapp.entity.*;
import com.bookingapp.bookingapp.exception.InsufficientAvailabilityException;
import com.bookingapp.bookingapp.exception.InvalidBookingStateException;
import com.bookingapp.bookingapp.repository.BookableResourceRepository;
import com.bookingapp.bookingapp.repository.BookingRepository;
import com.bookingapp.bookingapp.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BookableResourceRepository bookableResourceRepository;

    // ---------- Reads ----------

    public Booking getBookingById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + id));
    }

    public Booking getBookingByIdWithItems(Long id) {
        return bookingRepository.findByIdWithItems(id)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + id));
    }

    public List<Booking> getBookingsForUser(Long userId) {
        return bookingRepository.findByUserId(userId);
    }

    public List<Booking> getBookingsForUserByStatus(Long userId,  BookingStatus status) {
        return bookingRepository.findByUserIdAndStatus(userId, status);
    }

    // ---------- Commands ----------

    @Transactional
    public Booking createBooking(Long userId, LocalDateTime startTime, LocalDateTime endTime,
                                 Integer numberOfPeople, List<BookingItemRequest> requestedItems) {

        validateTimeRange(startTime, endTime);
        validateNumberOfPeople(numberOfPeople);
        Map<Long, Integer> quantitiesByResource = mergeAndValidateItems(requestedItems);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        // Locks rows in id order (see repository query) to avoid deadlocks
        Map<Long, BookableResource> resourceMap = new LinkedHashMap<>();
        bookableResourceRepository.findAllByIdWithLock(quantitiesByResource.keySet())
                .forEach(r -> resourceMap.put(r.getId(), r));

        Booking booking = new Booking(user, startTime, endTime, numberOfPeople);

        for (Map.Entry<Long, Integer> entry : quantitiesByResource.entrySet()) {
            BookableResource resource = resourceMap.get(entry.getKey());
            if (resource == null) {
                throw new EntityNotFoundException("Resource not found: " + entry.getKey());
            }

            int quantity = entry.getValue();
            if (resource.getAvailableUnits() < quantity) {
                throw new InsufficientAvailabilityException(
                        "Not enough availability for: " + resource.getName());
            }

            booking.addItem(resource, quantity, resource.getPricePerUnit());
            resource.setAvailableUnits(resource.getAvailableUnits() - quantity);
        }

        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking confirmBooking(Long id) {
        Booking booking = getBookingById(id);

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateException(
                    "Only pending bookings can be confirmed, current status: " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        return booking;
    }

    @Transactional
    public void cancelBooking(Long id) {
        Booking booking = getBookingByIdWithItems(id);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new InvalidBookingStateException("Booking already cancelled");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new InvalidBookingStateException("Booking already completed");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        restoreAvailability(booking);
    }

    @Transactional
    public Booking rescheduleBooking(Long id, LocalDateTime newStart, LocalDateTime newEnd) {
        Booking booking = getBookingById(id);

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateException("Only pending bookings can be rescheduled");
        }

        validateTimeRange(newStart, newEnd);

        booking.setStartTime(newStart);
        booking.setEndTime(newEnd);
        return booking;
    }

    // ---------- Helpers ----------

    private void restoreAvailability(Booking booking) {
        for (BookingItem item : booking.getItems()) {
            BookableResource resource = item.getBookableResource();
            resource.setAvailableUnits(resource.getAvailableUnits() + item.getQuantity());
        }
    }
    
    private Map<Long, Integer> mergeAndValidateItems(List<BookingItemRequest> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new IllegalArgumentException("A booking must contain at least one item");
        }

        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (BookingItemRequest req : requestedItems) {
            if (req.resourceId() == null) {
                throw new IllegalArgumentException("Resource id is required");
            }
            if (req.quantity() == null || req.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero");
            }
            merged.merge(req.resourceId(), req.quantity(), Integer::sum);
        }
        return merged;
    }

    private void validateTimeRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start and end time are required");
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
        if (start.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Start time cannot be in the past");
        }
    }

    private void validateNumberOfPeople(Integer numberOfPeople) {
        if (numberOfPeople == null || numberOfPeople <= 0) {
            throw new IllegalArgumentException("Number of people must be greater than zero");
        }
    }
}