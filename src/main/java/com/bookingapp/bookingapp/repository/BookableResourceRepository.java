package com.bookingapp.bookingapp.repository;

import com.bookingapp.bookingapp.entity.BookableResource;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BookableResourceRepository extends JpaRepository<BookableResource, Long> {

    /** Everything a customer can currently book, in a stable order for the UI. */
    List<BookableResource> findByActiveTrueOrderByTypeAscNameAsc();

    /**
     * Locks the given resources for the rest of the transaction, in ascending id order
     * to reduce deadlock risk between bookings that share resources.
     * Locks serialize changes across all dates for these resources, not just one slot.
     * Call this before reading usage; future admin capacity edits must coordinate on these locks too.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM BookableResource r WHERE r.id IN :ids ORDER BY r.id")
    List<BookableResource> findAllByIdWithLock(@Param("ids") Collection<Long> ids);
}