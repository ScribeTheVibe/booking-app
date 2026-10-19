package com.bookingapp.bookingapp.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One line of a booking: a resource and how many units of it.
 * The unit price is copied from the resource when the line is created.
 */
@Entity
@Table(name = "booking_items",
        indexes = {
                @Index(name = "idx_booking_items_resource", columnList = "resource_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_booking_resource", columnNames = {"booking_id", "resource_id"})
        },
        check = @CheckConstraint(constraint = "quantity > 0"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private BookableResource resource;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price_at_booking", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPriceAtBooking;

    @Version
    private Long version;

    /** Package-private: lines are created through Booking.addItem. */
    BookingItem(Booking booking, BookableResource resource, int quantity, BigDecimal unitPriceAtBooking) {
        this.booking = booking;
        this.resource = resource;
        this.quantity = quantity;
        this.unitPriceAtBooking = unitPriceAtBooking;
    }

    public BigDecimal subtotal() {
        return unitPriceAtBooking.multiply(BigDecimal.valueOf(quantity));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BookingItem that)) return false;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return BookingItem.class.hashCode();
    }
}