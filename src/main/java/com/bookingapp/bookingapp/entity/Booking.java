package com.bookingapp.bookingapp.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "user_id", nullable = false)
//    @Setter
//    private User user;

    @Column(nullable = false)
    @Setter
    private LocalDateTime startTime;

    @Column(nullable = false)
    @Setter
    private LocalDateTime endTime;

    @Column(nullable = false)
    @Setter
    private Integer numberOfPeople;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter
    private BookingStatus status;

    @Column(nullable = false)
    @Setter
    private BigDecimal totalPrice;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    @Getter(AccessLevel.NONE)
    private Long version;

    public Booking(LocalDateTime startTime,
                   LocalDateTime endTime, Integer numberOfPeople, BigDecimal totalPrice) {
//        this.user = user;
        this.startTime = startTime;
        this.endTime = endTime;
        this.numberOfPeople = numberOfPeople;
        this.totalPrice = totalPrice;
        this.status = BookingStatus.PENDING;
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
