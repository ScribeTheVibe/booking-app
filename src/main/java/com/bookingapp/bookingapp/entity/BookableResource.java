package com.bookingapp.bookingapp.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A ticket type or gear type with a fixed capacity per half-day.
 * Capacity is the same for every date.
 */
@Entity
@Table(name = "bookable_resources", check = @CheckConstraint(constraint = "total_capacity >= 0 AND unit_price >= 0"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookableResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceType type;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_capacity", nullable = false)
    private Integer totalCapacity;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    public BookableResource(String name, String description, ResourceType type,
                            BigDecimal unitPrice, int totalCapacity) {
        if (type == null) {
            throw new IllegalArgumentException("Resource type is required");
        }
        this.name = requireText(name, "Resource name");
        this.description = description;
        this.type = type;
        this.unitPrice = requireNonNegative(unitPrice, "Unit price");
        this.totalCapacity = requireNonNegative(totalCapacity, "Total capacity");
    }

    public void rename(String newName) {
        this.name = requireText(newName, "Resource name");
    }

    public void changeDescription(String newDescription) {
        this.description = newDescription;
    }

    public void changeUnitPrice(BigDecimal newPrice) {
        this.unitPrice = requireNonNegative(newPrice, "Unit price");
    }

    public void changeTotalCapacity(int newCapacity) {
        this.totalCapacity = requireNonNegative(newCapacity, "Total capacity");
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String label) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(label + " must be zero or greater");
        }
        return value;
    }

    private static int requireNonNegative(int value, String label) {
        if (value < 0) {
            throw new IllegalArgumentException(label + " must be zero or greater");
        }
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BookableResource that)) return false;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return BookableResource.class.hashCode();
    }
}