package com.bookingapp.bookingapp.dto;

import com.bookingapp.bookingapp.entity.BookableResource;
import com.bookingapp.bookingapp.entity.ResourceType;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminResourceResponse(
        Long id,
        String name,
        String description,
        ResourceType type,
        BigDecimal unitPrice,
        int totalCapacity,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
    public static AdminResourceResponse from(BookableResource r) {
        return new AdminResourceResponse(
                r.getId(), r.getName(), r.getDescription(), r.getType(),
                r.getUnitPrice(), r.getTotalCapacity(), r.isActive(),
                r.getCreatedAt(), r.getUpdatedAt(), r.getVersion());
    }
}