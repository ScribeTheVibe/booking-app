package com.bookingapp.bookingapp.service;

import com.bookingapp.bookingapp.dto.BookingItemRequest;
import com.bookingapp.bookingapp.dto.ResourceUsage;
import com.bookingapp.bookingapp.dto.SessionAvailability;
import com.bookingapp.bookingapp.entity.BookableResource;
import com.bookingapp.bookingapp.entity.Booking;
import com.bookingapp.bookingapp.entity.BookingItem;
import com.bookingapp.bookingapp.entity.BookingStatus;
import com.bookingapp.bookingapp.entity.HalfDay;
import com.bookingapp.bookingapp.entity.ResortTime;
import com.bookingapp.bookingapp.entity.ResourceType;
import com.bookingapp.bookingapp.entity.Session;
import com.bookingapp.bookingapp.entity.Setting;
import com.bookingapp.bookingapp.entity.User;
import com.bookingapp.bookingapp.exception.InsufficientAvailabilityException;
import com.bookingapp.bookingapp.exception.InvalidBookingStateException;
import com.bookingapp.bookingapp.repository.BookableResourceRepository;
import com.bookingapp.bookingapp.repository.BookingItemRepository;
import com.bookingapp.bookingapp.repository.BookingRepository;
import com.bookingapp.bookingapp.repository.SettingRepository;
import com.bookingapp.bookingapp.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * User-facing booking operations. Every method acts on behalf of ONE user:
 * the caller's id must come from the authenticated principal, never from a request body.
 * Admin operations belong in a separate AdminBookingService.
 * <p>
 * Capacity is derived: for a date, usage of a resource in a half-day is the sum of quantities
 * of active holds whose session covers that half-day. Rejected, dropped and completed
 * bookings do not count. See Booking.holdsCapacity.
 * Reads are advisory; write transactions lock resources before checking capacity.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingService {

    /** The longest date range the browsing screen may ask for in one call. */
    private static final int MAX_BROWSE_DAYS = 120;

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BookableResourceRepository bookableResourceRepository;
    private final BookingItemRepository bookingItemRepository;
    private final SettingRepository settingRepository;
    private final Clock clock;

    /** Booked units of one resource in one half-day of one date. */
    private record UsageKey(Long resourceId, LocalDate date, HalfDay halfDay) {
    }

    // ---------- Reads ----------

    public Booking getMyBooking(Long bookingId, Long userId) {
        return findOwnedWithItems(bookingId, userId);
    }

    public List<Booking> getMyBookings(Long userId) {
        return bookingRepository.findByUserIdOrderByDateDescIdDesc(userId);
    }

    /** Filters the customer-visible state, since dropped / completed, are not stored statuses. */
    public List<Booking> getMyBookingsByStatus(Long userId, BookingStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status is required");
        }
        Clock asOf = Clock.fixed(clock.instant(), ResortTime.ZONE);
        return bookingRepository.findByUserIdOrderByDateDescIdDesc(userId).stream()
                .filter(booking -> booking.effectiveStatus(asOf) == status)
                .toList();
    }

    /**
     * The availability grid: for the given group and gear, every bookable session on every open
     * date in the range, whether it fits, and the price. Sessions that have already started are
     * left out. Nothing is locked here; the booking itself re-checks under lock.
     */
    public List<SessionAvailability> findAvailability(List<BookingItemRequest> requestedItems,
                                                      LocalDate fromDate, LocalDate toDate) {
        validateDateRange(fromDate, toDate);
        Map<Long, Integer> quantities = mergeAndValidateItems(requestedItems);
        Map<Long, BookableResource> resources =
                indexById(bookableResourceRepository.findAllById(quantities.keySet()));
        requireBookable(quantities.keySet(), resources);
        requireTickets(quantities, resources);

        List<ResourceUsage> rows = bookingItemRepository.findUsage(quantities.keySet(), fromDate, toDate);
        Instant now = clock.instant();
        Map<UsageKey, Long> usage = toHalfDayUsage(rows, now);
        BigDecimal totalPrice = priceOf(quantities, resources);

        List<SessionAvailability> result = new ArrayList<>();
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            if (!ResortTime.isOpenDay(date)) {
                continue;
            }
            for (Session session : Session.values()) {
                if (hasStarted(date, session, now)) {
                    continue;
                }
                boolean available = findShortfall(quantities, resources, date, session, usage).isEmpty();
                result.add(new SessionAvailability(date, session, available, totalPrice));
            }
        }
        return result;
    }

    // ---------- Commands ----------

    /**
     * READ_COMMITTED so the usage query after the lock always sees bookings committed by
     * whoever held the lock before us, to any database (MySQL defaults to REPEATABLE READ).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking createBooking(Long userId, LocalDate date, Session session, List<BookingItemRequest> requestedItems) {
        requireBookableSlot(date, session);
        Map<Long, Integer> quantities = mergeAndValidateItems(requestedItems);

        // Lock first, in id order (see repository query), then read usage.
        Map<Long, BookableResource> resources =
                indexById(bookableResourceRepository.findAllByIdWithLock(quantities.keySet()));
        requireBookable(quantities.keySet(), resources);
        requireTickets(quantities, resources);

        Map<UsageKey, Long> usage = toHalfDayUsage(
                bookingItemRepository.findUsage(quantities.keySet(), date, date), clock.instant());
        requireCapacity(quantities, resources, date, session, usage);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        // Waiting for locks or database reads may have crossed the session start.
        requireBookableSlot(date, session);
        Booking booking = new Booking(user, date, session);
        quantities.forEach((resourceId, quantity) -> booking.addItem(resources.get(resourceId), quantity));
        return bookingRepository.save(booking);
    }

    /**
     * Moves a booking to another date. Session, group size, items, price and status stay the same.
     * If the new date can't take it, nothing changes. A concurrent change to the same booking
     * makes the final update fail with an optimistic-lock exception (Booking has @Version);
     * the controller layer should report that as a conflict.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking rescheduleMyBooking(Long bookingId, Long userId, LocalDate newDate) {
        Booking booking = findOwnedWithItems(bookingId, userId);

        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException("Only pending or confirmed bookings can be rescheduled");
        }
        if (booking.hasStarted(clock)) {
            throw new InvalidBookingStateException("A booking whose session has started cannot be rescheduled");
        }
        requireBeforeRescheduleDeadline(booking);
        requireBookableSlot(newDate, booking.getSession());
        if (newDate.equals(booking.getDate())) {
            throw new IllegalArgumentException("New date must differ from the current date");
        }

        Map<Long, Integer> quantities = booking.getItems().stream()
                .collect(Collectors.toMap(item -> item.getResource().getId(), BookingItem::getQuantity));

        // Same lock as createBooking, so the two can't interleave on shared resources.
        Map<Long, BookableResource> resources =
                indexById(bookableResourceRepository.findAllByIdWithLock(quantities.keySet()));

        requireBookable(quantities.keySet(), resources);
        // This booking's own current hold must not count against its new date.
        Map<UsageKey, Long> usage = toHalfDayUsage(bookingItemRepository.findUsageExcludingBooking(
                quantities.keySet(), newDate, newDate, booking.getId()), clock.instant());
        requireCapacity(quantities, resources, newDate, booking.getSession(), usage);

        // Lock waits and database reads may have crossed either cutoff.
        requireBeforeRescheduleDeadline(booking);
        requireBookableSlot(newDate, booking.getSession());
        // JPA dirty checking persists this move at commit; failure rolls back the transaction.
        booking.moveTo(newDate);
        return booking;
    }

    // ---------- Ownership-scoped lookups ----------

    private Booking findOwnedWithItems(Long bookingId, Long userId) {
        return bookingRepository.findByIdAndUserIdWithItems(bookingId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));
    }

    // ---------- Capacity ----------

    /** Sums only active holds per resource, date and half-day at one instant. */
    private Map<UsageKey, Long> toHalfDayUsage(List<ResourceUsage> rows, Instant now) {
        Map<UsageKey, Long> usage = new HashMap<>();
        Clock asOf = Clock.fixed(now, ResortTime.ZONE);
        for (ResourceUsage row : rows) {
            if (!Booking.holdsCapacity(row.status(), row.session(), row.date(), asOf)) {
                continue;
            }
            // A full-day quantity consumes the same number of units in both halves.
            for (HalfDay halfDay : row.session().halfDays()) {
                usage.merge(new UsageKey(row.resourceId(), row.date(), halfDay), row.quantity(), Math::addExact);
            }
        }
        return usage;
    }

    /**
     * The first requested resource that doesn't fit in some half-day covered by the session,
     * or empty if everything fits.
     */
    private Optional<BookableResource> findShortfall(Map<Long, Integer> quantities,
                                                     Map<Long, BookableResource> resources,
                                                     LocalDate date, Session session,
                                                     Map<UsageKey, Long> usage) {
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            BookableResource resource = resources.get(entry.getKey());
            for (HalfDay halfDay : session.halfDays()) {
                long used = usage.getOrDefault(new UsageKey(resource.getId(), date, halfDay), 0L);
                if (entry.getValue() > (long) resource.getTotalCapacity() - used) {
                    return Optional.of(resource);
                }
            }
        }
        return Optional.empty();
    }

    private void requireCapacity(Map<Long, Integer> quantities, Map<Long, BookableResource> resources,
                                 LocalDate date, Session session, Map<UsageKey, Long> usage) {
        findShortfall(quantities, resources, date, session, usage).ifPresent(resource -> {
            throw new InsufficientAvailabilityException("Not enough availability for "
                    + resource.getName() + " on " + date + " (" + session + ")");
        });
    }

    /** Must match how Booking.addItem prices a line: current unit price times quantity. */
    private BigDecimal priceOf(Map<Long, Integer> quantities, Map<Long, BookableResource> resources) {
        return quantities.entrySet().stream()
                .map(e -> resources.get(e.getKey()).getUnitPrice().multiply(BigDecimal.valueOf(e.getValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------- Rules ----------

    private boolean hasStarted(LocalDate date, Session session, Instant now) {
        return !now.isBefore(session.startAt(date).toInstant());
    }

    private void requireBookableSlot(LocalDate date, Session session) {
        if (date == null) {
            throw new IllegalArgumentException("Date is required");
        }
        if (session == null) {
            throw new IllegalArgumentException("Session is required");
        }
        if (!ResortTime.isOpenDay(date)) {
            throw new IllegalArgumentException("The resort is only open Friday to Sunday");
        }
        if (hasStarted(date, session, clock.instant())) {
            throw new IllegalArgumentException("This session has already started");
        }
    }

    /** Allowed only before (original session start minus the admin-set number of hours). */
    private void requireBeforeRescheduleDeadline(Booking booking) {
        int hours = settingRepository.findById(Setting.RESCHEDULE_DEADLINE_HOURS)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing setting: " + Setting.RESCHEDULE_DEADLINE_HOURS))
                .asInt();
        if (hours < 0) {
            throw new IllegalStateException(Setting.RESCHEDULE_DEADLINE_HOURS + " must not be negative");
        }

        Instant deadline = booking.startsAt().toInstant().minus(Duration.ofHours(hours));
        if (!clock.instant().isBefore(deadline)) {
            throw new InvalidBookingStateException("The reschedule deadline for this booking has passed");
        }
    }

    // ---------- Validation helpers ----------

    private Map<Long, BookableResource> indexById(List<BookableResource> resources) {
        return resources.stream().collect(Collectors.toMap(BookableResource::getId, r -> r));
    }

    private void requireBookable(Collection<Long> requestedIds, Map<Long, BookableResource> byId) {
        for (Long id : requestedIds) {
            BookableResource resource = byId.get(id);
            if (resource == null) {
                throw new EntityNotFoundException("Resource not found: " + id);
            }
            if (!resource.isActive()) {
                throw new IllegalArgumentException("Resource is not available: " + resource.getName());
            }
        }
    }

    /** Combines duplicate resource lines before ticket counts, pricing and capacity checks. */
    private Map<Long, Integer> mergeAndValidateItems(List<BookingItemRequest> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new IllegalArgumentException("A booking must contain at least one item");
        }

        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (BookingItemRequest req : requestedItems) {
            if (req == null) {
                throw new IllegalArgumentException("Item is required");
            }
            if (req.resourceId() == null) {
                throw new IllegalArgumentException("Resource id is required");
            }
            if (req.quantity() == null || req.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero");
            }
            try {
                merged.merge(req.resourceId(), req.quantity(), Math::addExact);
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Total quantity is too large for resource: " + req.resourceId(), e);
            }
        }
        return merged;
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("From and to dates are required");
        }
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("The end date must not be before the start date");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) >= MAX_BROWSE_DAYS) {
            throw new IllegalArgumentException("Date range is too long (maximum " + MAX_BROWSE_DAYS + " days)");
        }
    }

    private void requireTickets(Map<Long, Integer> quantities, Map<Long, BookableResource> resources) {
        boolean hasTicket = quantities.keySet().stream()
                .anyMatch(id -> resources.get(id).getType() == ResourceType.TICKET);
        if (!hasTicket) {
            throw new IllegalArgumentException("A booking must contain at least one ticket");
        }
    }
}