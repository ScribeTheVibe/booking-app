package com.bookingapp.bookingapp.repository;

import com.bookingapp.bookingapp.entity.BookableResource;
import com.bookingapp.bookingapp.entity.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookableResourceRepository extends JpaRepository<BookableResource, Long> {
    List<BookableResource> findByType(ResourceType type);
    List<BookableResource> findByAvailableUnitsGreaterThan(Integer units);
}
