package com.bookingapp.bookingapp.repository;

import com.bookingapp.bookingapp.entity.Booking;
import com.bookingapp.bookingapp.entity.BookingStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Customer-facing queries only. Every query that returns bookings to be shown loads
 * items and their resources up front, so mapping to DTOs never triggers lazy loading.
 * Admin queries (by date, by status) will be added with the admin service.
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"items", "items.resource"})
    @Query("SELECT b FROM Booking b WHERE b.id = :id AND b.user.id = :userId")
    Optional<Booking> findByIdAndUserIdWithItems(@Param("id") Long id,
                                                 @Param("userId") Long userId);

    @EntityGraph(attributePaths = {"items", "items.resource"})
    List<Booking> findByUserIdOrderByDateDescIdDesc(Long userId);
}