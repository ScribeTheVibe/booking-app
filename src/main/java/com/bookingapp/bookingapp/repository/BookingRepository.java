package com.bookingapp.bookingapp.repository;

import com.bookingapp.bookingapp.entity.Booking;
import com.bookingapp.bookingapp.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserId(Long userId);
    List<Booking> findByStatus(BookingStatus status);

    @Query("SELECT b FROM Booking b JOIN FETCH b.items WHERE b.id = :id")
    Optional<Booking> findByIdWithItems(@Param("id") Long id);
}
