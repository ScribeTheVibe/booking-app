package com.bookingapp.bookingapp.repository;

import com.bookingapp.bookingapp.entity.BookableResource;
import com.bookingapp.bookingapp.entity.Booking;
import com.bookingapp.bookingapp.entity.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserId(Long userId);
    List<Booking> findByStatus(BookingStatus status);
    List<Booking> findByStatusAndEndTimeBefore(BookingStatus status, LocalDateTime time);
    List<Booking> findByUserIdAndStatus(Long userId, BookingStatus status);

    @Query("SELECT b FROM Booking b " +
            "LEFT JOIN FETCH b.items i " +
            "LEFT JOIN FETCH i.bookableResource " +
            "WHERE b.id = :id")
    Optional<Booking> findByIdWithItems(@Param("id") Long id);
}
