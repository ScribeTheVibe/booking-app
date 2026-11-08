package com.bookingapp.bookingapp.service;

import com.bookingapp.bookingapp.entity.*;
import com.bookingapp.bookingapp.exception.ResourceConflictException;
import com.bookingapp.bookingapp.repository.BookableResourceRepository;
import com.bookingapp.bookingapp.repository.BookingItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Admin-facing management of bookable resources (ticket and gear types).
 * <p>
 * Every mutation takes the same pessimistic row lock the booking flow takes
 * ({@link BookableResourceRepository#findByIdWithLock} and
 * {@link BookableResourceRepository#findAllByIdWithLock}), so a change can never
 * interleave with a booking that is reserving this resource. Mutations load the
 * entity under that lock and rely on dirty checking to persist.
 * <p>
 * Write methods follow one rule: validate and run every query first, mutate the
 * entity last. Hibernate auto-flushes dirty entities before a query, so a query
 * that runs after a field change would write an extra UPDATE (and bump the version
 * a second time).
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

    private final BookableResourceRepository resourceRepository;
    private final BookingItemRepository bookingItemRepository;

    // ---------------------------------------------------------------- reads

    public BookableResource getResourceById(Long id) {
        return resourceRepository.findById(id).orElseThrow(() -> notFound(id));
    }

    public List<BookableResource> findResources(ResourceStatusFilter filter) {
        return switch (filter) {
            case ALL      -> resourceRepository.findAllByOrderByTypeAscNameAsc();
            case ACTIVE   -> resourceRepository.findByActiveTrueOrderByTypeAscNameAsc();
            case INACTIVE -> resourceRepository.findByActiveFalseOrderByTypeAscNameAsc();
        };
    }

    // --------------------------------------------------------------- writes

    @Transactional
    public BookableResource createResource(
            String name,
            String description,
            ResourceType type,
            BigDecimal unitPrice,
            int totalCapacity) {

        // The constructor validates and trims; the transient entity is not in the
        // persistence context, so the query below cannot trigger a flush.
        BookableResource resource = new BookableResource(
                name, description, type, normalizePrice(unitPrice), totalCapacity);

        requireUniqueTypeAndName(resource.getType(), resource.getName(), null);

        return resourceRepository.save(resource);
    }

    /**
     * Updates name, description, type, price and capacity in one transaction and one
     * UPDATE statement. Only allowed while no booking item references the resource,
     * in every case: once a resource has been booked it can only be deactivated.
     * <p>
     * The booking check runs under the same row lock the booking flow takes before it
     * inserts items, so no booking can slip in between the check and the commit.
     * <p>
     * {@code expectedVersion} is the version the admin UI last saw. It is compared after
     * the lock is taken, so a stale form fails instead of silently overwriting a newer edit.
     */
    @Transactional
    public BookableResource updateResource(
            Long id,
            Long expectedVersion,
            String name,
            String description,
            ResourceType type,
            BigDecimal unitPrice,
            int totalCapacity) {

        BookableResource resource = getLocked(id);

        if (!Objects.equals(resource.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(BookableResource.class, id);
        }

        // 1. Validate and normalize without touching the entity.
        String trimmedName = requireText(name);
        BigDecimal price = normalizePrice(unitPrice);
        if (type == null) {
            throw new IllegalArgumentException("Resource type is required");
        }
        if (totalCapacity < 0) {
            throw new IllegalArgumentException("Total capacity must be zero or greater");
        }

        // 2. Run every query while the entity is still clean, so nothing auto-flushes.
        if (bookingItemRepository.existsByResourceId(id)) {
            throw new ResourceConflictException(
                    "Resource " + id + " is used by bookings and cannot be edited; deactivate it instead");
        }
        requireUniqueTypeAndName(type, trimmedName, id);

        // 3. Mutate last, so Hibernate writes a single UPDATE at commit.
        resource.rename(trimmedName);
        resource.changeDescription(description);
        resource.changeType(type);
        resource.changeUnitPrice(price);
        resource.changeTotalCapacity(totalCapacity);

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
     * @param excludeId the resource being updated, or null when creating.
     *                  Two queries because {@code id <> NULL} is never true in SQL.
     */
    private void requireUniqueTypeAndName(ResourceType type, String name, Long excludeId) {
        boolean taken = excludeId == null
                ? resourceRepository.existsByTypeAndNameIgnoreCase(type, name)
                : resourceRepository.existsByTypeAndNameIgnoreCaseAndIdNot(type, name, excludeId);
        if (taken) {
            throw new ResourceConflictException(
                    "A " + type + " resource named '" + name + "' already exists");
        }
    }

    private static String requireText(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Resource name is required");
        }
        return name.trim();
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