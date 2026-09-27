package com.bookingapp.bookingapp.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "bookable_resources")
@Getter
@NoArgsConstructor
public class BookableResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "bookableResource", fetch = FetchType.LAZY)
    private List<BookingItem> bookingItems;

    @Column(nullable = false)
    @Setter
    private String name;

    @Column(columnDefinition = "TEXT")
    @Setter
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceType type;

    @Column(nullable = false)
    @Setter
    private BigDecimal pricePerUnit;

    @Column(nullable = false)
    @Setter
    private Integer totalCapacity;

    @Column(nullable = false)
    @Setter
    private Integer availableUnits;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Setter
    private LocalDateTime updatedAt;

    @Version
    @Getter(AccessLevel.NONE)
    private Long version;

    public BookableResource(String name, String description, ResourceType type,
                            BigDecimal pricePerUnit, Integer totalCapacity) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.pricePerUnit = pricePerUnit;
        this.totalCapacity = totalCapacity;
        this.availableUnits = totalCapacity;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

