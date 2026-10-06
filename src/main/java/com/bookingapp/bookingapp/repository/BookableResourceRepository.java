package com.bookingapp.bookingapp.repository;

import com.bookingapp.bookingapp.entity.BookableResource;
import com.bookingapp.bookingapp.entity.ResourceType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BookableResourceRepository extends JpaRepository<BookableResource, Long> {
    List<BookableResource> findByType(ResourceType type);
    List<BookableResource> findByAvailableUnitsGreaterThan(Integer units);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM BookableResource r WHERE r.id IN :ids ORDER BY r.id")
    List<BookableResource> findAllByIdWithLock(@Param("ids") Collection<Long> ids);
}
