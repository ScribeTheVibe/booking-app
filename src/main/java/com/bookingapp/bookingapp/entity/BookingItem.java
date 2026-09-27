package com.bookingapp.bookingapp.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "booking_items", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"booking_id", "resource_id"})
})
@Getter
@NoArgsConstructor
public class BookingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", nullable = false)
    private BookableResource bookableResource;

    @Column(nullable = false)
    @Setter
    private Integer quantity;

    @Column(nullable = false)
    @Setter
    private BigDecimal priceAtBooking;

    @Version
    @Getter(AccessLevel.NONE)
    private Long version;

    public BookingItem(Booking booking, BookableResource bookableResource, Integer quantity, BigDecimal priceAtBooking) {
        this.booking = booking;
        this.bookableResource = bookableResource;
        this.quantity = quantity;
        this.priceAtBooking = priceAtBooking;
    }

    public BigDecimal getSubtotal() {
        return priceAtBooking.multiply(BigDecimal.valueOf(quantity));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BookingItem that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
