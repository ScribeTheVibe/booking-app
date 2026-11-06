package com.bookingapp.bookingapp.service;

import com.bookingapp.bookingapp.entity.*;
import com.bookingapp.bookingapp.repository.BookableResourceRepository;
import com.bookingapp.bookingapp.repository.BookingItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin-facing management of bookable resources (ticket and gear types).
 * <p>
 * Every mutation takes the same pessimistic lock the booking flow takes
 * ({@link BookableResourceRepository#findAllByIdWithLock}), so a change can never
 * interleave with a booking that is reading usage or reserving capacity.
 * Mutations load the entity under that lock and rely on dirty checking to persist.
 * <p>
 * Reads are read-only transactions; every write method opts back in with a plain
 * {@code @Transactional}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResourceService {

    /** Matches the column definition: precision 10, scale 2. */
    private static final int PRICE_SCALE = 2;
    private static final int PRICE_MAX_INTEGER_DIGITS = 8;

    /** Statuses that hold capacity. Adjust if your BookingStatus differs. */
    private static final Set<BookingStatus> CAPACITY_HOLDING =
            EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);

    private final BookableResourceRepository resourceRepository;
    private final BookingItemRepository bookingItemRepository;
    private final Clock clock;

    // ---------------------------------------------------------------- reads

    public BookableResource getResourceById(Long id) {
        return resourceRepository.findById(id).orElseThrow(() -> notFound(id));
    }

    /** Everything, including inactive resources. For admin screens. */
    public List<BookableResource> findAll() {
        return resourceRepository.findAll();
    }

    /** What a customer can currently book, in a stable order for the UI. */
    public List<BookableResource> findActive() {
        return resourceRepository.findByActiveTrueOrderByTypeAscNameAsc();
    }

    // --------------------------------------------------------------- writes

    @Transactional
    public BookableResource createResource(String name,
                                           String description,
                                           ResourceType type,
                                           BigDecimal unitPrice,
                                           int totalCapacity) {
        BookableResource resource = new BookableResource(
                name, description, type, normalizePrice(unitPrice), totalCapacity);
        return resourceRepository.save(resource);
    }

    @Transactional
    public BookableResource rename(Long id, String newName) {
        BookableResource resource = getLocked(id);
        resource.rename(newName);
        return resource;
    }

    @Transactional
    public BookableResource changeDescription(Long id, String newDescription) {
        BookableResource resource = getLocked(id);
        resource.changeDescription(newDescription);
        return resource;
    }

    /**
     * Affects new bookings only. Existing booking items must keep the price they
     * were booked at (snapshot on BookingItem), which is also what keeps reschedule
     * price-neutral.
     */
    @Transactional
    public BookableResource changeUnitPrice(Long id, BigDecimal newPrice) {
        BookableResource resource = getLocked(id);
        resource.changeUnitPrice(normalizePrice(newPrice));
        return resource;
    }

    /**
     * Capacity applies to every date, so lowering it is only allowed if no future
     * half-day slot already has more reserved (pending + confirmed) than the new value.
     * The lock held here guarantees no booking can slip in between this check and commit.
     */
    @Transactional
    public BookableResource changeTotalCapacity(Long id, int newCapacity) {
        BookableResource resource = getLocked(id);

        if (newCapacity < resource.getTotalCapacity()) {
            int peak = peakReservedQuantity(id, LocalDate.now(clock));
            if (newCapacity < peak) {
                throw new IllegalStateException(
                        "Cannot lower capacity to " + newCapacity + ": a future session already has "
                                + peak + " reserved");
            }
        }

        resource.changeTotalCapacity(newCapacity);
        return resource;
    }

    /** Makes the resource bookable again. Idempotent. */
    @Transactional
    public BookableResource activate(Long id) {
        BookableResource resource = getLocked(id);
        resource.activate();
        return resource;
    }

    /**
     * Stops new bookings from using the resource. Existing bookings are untouched and
     * stay reschedulable, because their items do not change. Idempotent.
     */
    @Transactional
    public BookableResource deactivate(Long id) {
        BookableResource resource = getLocked(id);
        resource.deactivate();
        return resource;
    }

    // -------------------------------------------------------------- helpers

    private BookableResource getLocked(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Resource id is required");
        }
        return resourceRepository.findByIdWithLock(id).orElseThrow(() -> notFound(id));
    }

    /**
     * Highest quantity of this resource reserved in any single half-day slot from
     * {@code from} onward. A full-day booking counts in both the morning and evening
     * halves, so each date's load is max(morning + fullDay, evening + fullDay).
     * Slots earlier today that have already started are still counted, which only
     * errs on the safe side.
     */
    private int peakReservedQuantity(Long resourceId, LocalDate from) {
        Map<LocalDate, long[]> halves = new HashMap<>(); // [morning, evening]
        for (var row : bookingItemRepository.findUsageBySession(resourceId, from, CAPACITY_HOLDING)) {
            long qty = row.getQuantity();
            long[] day = halves.computeIfAbsent(row.getSessionDate(), d -> new long[2]);
            if (row.getSession() != Session.EVENING) day[0] += qty; // MORNING, FULL_DAY
            if (row.getSession() != Session.MORNING) day[1] += qty; // EVENING, FULL_DAY
        }
        return (int) halves.values().stream()
                .mapToLong(day -> Math.max(day[0], day[1]))
                .max()
                .orElse(0);
    }

    private static BigDecimal normalizePrice(BigDecimal price) {
        if (price == null) {
            throw new IllegalArgumentException("Unit price is required");
        }
        if (price.signum() < 0) {
            throw new IllegalArgumentException("Unit price must be zero or greater");
        }
        BigDecimal scaled;
        try {
            scaled = price.setScale(PRICE_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Unit price can have at most " + PRICE_SCALE + " decimal places");
        }
        if (scaled.precision() - scaled.scale() > PRICE_MAX_INTEGER_DIGITS) {
            throw new IllegalArgumentException("Unit price is too large");
        }
        return scaled;
    }

    private static EntityNotFoundException notFound(Long id) {
        return new EntityNotFoundException("Resource " + id + " not found");
    }
}