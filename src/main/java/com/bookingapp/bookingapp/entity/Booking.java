package com.bookingapp.bookingapp.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One customer, one date, one session. Everyone in the group belongs to this booking.
 * Holds capacity while PENDING or CONFIRMED. "Dropped" and "completed" are derived, not stored.
 * The party size is derived too: it is the number of ticket units on the booking.
 */
@Entity
@Table(name = "bookings",
        indexes = {
                @Index(name = "idx_bookings_date_status", columnList = "booking_date, status"),
                @Index(name = "idx_bookings_user", columnList = "user_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "booking_date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Session session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // Concurrent edits to this booking must not silently overwrite each other.
    @Version
    private Long version;

    public Booking(User user, LocalDate date, Session session) {
        if (user == null) throw new IllegalArgumentException("User is required");
        if (session == null) throw new IllegalArgumentException("Session is required");
        requireOpenDay(date);

        this.user = user;
        this.date = date;
        this.session = session;
        this.status = BookingStatus.PENDING;
    }

    /**
     * Adds a line to the booking and copies the resource's current price onto it,
     * so later price changes don't affect this booking.
     */
    public void addItem(BookableResource resource, int quantity) {
        if (status != BookingStatus.PENDING) {
            throw new IllegalStateException("Items can only be added to a pending booking");
        }
        if (resource == null) throw new IllegalArgumentException("Resource is required");
        if (!resource.isActive()) throw new IllegalArgumentException("Resource is not available: " + resource.getName());
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be greater than zero");
        if (items.stream().anyMatch(i -> i.getResource().equals(resource))) {
            throw new IllegalArgumentException("Resource already added: " + resource.getName());
        }

        items.add(new BookingItem(this, resource, quantity, resource.getUnitPrice()));
    }

    /**
     * Moves the booking to another date. Session, items, and status are unchanged.
     * Deadline and capacity rules are checked by the service before calling this.
     */
    public void moveTo(LocalDate newDate) {
        if (status != BookingStatus.PENDING && status != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only pending or confirmed bookings can be moved");
        }
        requireOpenDay(newDate);
        if (newDate.equals(this.date)) {
            throw new IllegalArgumentException("New date must differ from the current date");
        }
        this.date = newDate;
    }

    public void confirm() {
        confirm(Clock.system(ResortTime.ZONE));
    }

    public void confirm(Clock clock) {
        if (effectiveStatus(clock) != BookingStatus.PENDING) {
            throw new IllegalStateException("Only pending bookings that have not started can be confirmed");
        }
        this.status = BookingStatus.CONFIRMED;
    }

    public void reject(Clock clock) {
        BookingStatus current = effectiveStatus(clock);
        if (current != BookingStatus.PENDING && current != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only pending or confirmed bookings can be rejected");
        }
        this.status = BookingStatus.REJECTED;
    }

    // ---------- Derived state (nothing here is stored) ----------

    /** Customer-visible lifecycle state; getStatus() is the persisted review decision. */
    public BookingStatus effectiveStatus(Clock clock) {
        return effectiveStatus(status, session, date, clock);
    }

    public static BookingStatus effectiveStatus(BookingStatus status, Session session, LocalDate date, Clock clock) {
        Instant now = clock.instant();
        if (status == BookingStatus.PENDING && !now.isBefore(session.startAt(date).toInstant())) {
            return BookingStatus.DROPPED;
        }
        if (status == BookingStatus.CONFIRMED && !now.isBefore(session.endAt(date).toInstant())) {
            return BookingStatus.COMPLETED;
        }
        return status;
    }

    public ZonedDateTime startsAt() {
        return session.startAt(date);
    }

    public ZonedDateTime endsAt() {
        return session.endAt(date);
    }

    public boolean hasStarted(Clock clock) {
        return !clock.instant().isBefore(startsAt().toInstant());
    }

    public boolean hasEnded(Clock clock) {
        return !clock.instant().isBefore(endsAt().toInstant());
    }

    /** A pending booking whose session has started. Static so usage rows can apply the same rule. */
    public static boolean isDropped(BookingStatus status, Session session, LocalDate date, Clock clock) {
        return effectiveStatus(status, session, date, clock) == BookingStatus.DROPPED;
    }

    /** Instance convenience, delegating to the static rule. */
    public boolean isDropped(Clock clock) {
        return isDropped(status, session, date, clock);
    }

    public boolean isCompleted(Clock clock) {
        return effectiveStatus(clock) == BookingStatus.COMPLETED;
    }

    /** Confirmed bookings until session end, plus pending bookings until session start. */
    public boolean holdsCapacity(Clock clock) {
        return holdsCapacity(status, session, date, clock);
    }

    public static boolean holdsCapacity(BookingStatus status, Session session, LocalDate date, Clock clock) {
        BookingStatus current = effectiveStatus(status, session, date, clock);
        return current == BookingStatus.PENDING || current == BookingStatus.CONFIRMED;
    }

    public BigDecimal totalPrice() {
        return items.stream()
                .map(BookingItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Party size: one ticket unit per person. Needs items and items.resource loaded. */
    public int numberOfPeople() {
        return items.stream()
                .filter(i -> i.getResource().getType() == ResourceType.TICKET)
                .mapToInt(BookingItem::getQuantity)
                .sum();
    }

    /** Keep collection changes inside the aggregate so callers cannot bypass addItem rules. */
    public List<BookingItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    // ---------- Lifecycle ----------

    @PrePersist
    protected void onCreate() {
        validateItems();
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        validateItems();
        updatedAt = Instant.now();
    }

    /** Final persistence guard: a booking needs at least one item, and at least one ticket. */
    private void validateItems() {
        if (items.isEmpty()) {
            throw new IllegalStateException("A booking must contain at least one item");
        }
        if (numberOfPeople() <= 0) {
            throw new IllegalStateException("A booking must contain at least one ticket");
        }
    }

    private static void requireOpenDay(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("Date is required");
        if (!ResortTime.isOpenDay(date)) {
            throw new IllegalArgumentException("The resort is only open Friday to Sunday");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Booking that)) return false;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Booking.class.hashCode();
    }
}