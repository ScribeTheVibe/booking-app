package com.bookingapp.bookingapp.repository;

import com.bookingapp.bookingapp.dto.ResourceUsage;
import com.bookingapp.bookingapp.entity.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {

    /**
     * Booked units per resource, date, session and status for the given dates (inclusive).
     * Only pending and confirmed decisions can hold capacity. The caller additionally
     * filters time-derived dropped and completed states at a consistent instant.
     */
    @Query("""
            SELECT new com.bookingapp.bookingapp.dto.ResourceUsage(
                bi.resource.id, b.date, b.session, b.status, SUM(bi.quantity))
            FROM BookingItem bi
            JOIN bi.booking b
            WHERE bi.resource.id IN :resourceIds
              AND b.date BETWEEN :fromDate AND :toDate
              AND b.status IN (com.bookingapp.bookingapp.entity.BookingStatus.PENDING,
                               com.bookingapp.bookingapp.entity.BookingStatus.CONFIRMED)
            GROUP BY bi.resource.id, b.date, b.session, b.status
            """)
    List<ResourceUsage> findUsage(@Param("resourceIds") Collection<Long> resourceIds,
                                  @Param("fromDate") LocalDate fromDate,
                                  @Param("toDate") LocalDate toDate);

    /** Same as findUsage, but ignores one booking. Used when rescheduling that booking. */
    @Query("""
            SELECT new com.bookingapp.bookingapp.dto.ResourceUsage(
                bi.resource.id, b.date, b.session, b.status, SUM(bi.quantity))
            FROM BookingItem bi
            JOIN bi.booking b
            WHERE bi.resource.id IN :resourceIds
              AND b.date BETWEEN :fromDate AND :toDate
              AND b.id <> :excludeBookingId
              AND b.status IN (com.bookingapp.bookingapp.entity.BookingStatus.PENDING,
                               com.bookingapp.bookingapp.entity.BookingStatus.CONFIRMED)
            GROUP BY bi.resource.id, b.date, b.session, b.status
            """)
    List<ResourceUsage> findUsageExcludingBooking(@Param("resourceIds") Collection<Long> resourceIds,
                                                  @Param("fromDate") LocalDate fromDate,
                                                  @Param("toDate") LocalDate toDate,
                                                  @Param("excludeBookingId") Long excludeBookingId);
}