package com.bookingapp.bookingapp.dto;

import com.bookingapp.bookingapp.entity.ResourceType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UpdateResourceRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2000) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal unitPrice,
        @NotNull @Min(0) @Max(100_000) Integer totalCapacity,
        @NotNull ResourceType type,
        @NotNull Long version
) {}