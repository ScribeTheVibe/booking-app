package com.bookingapp.bookingapp.dto;

import com.bookingapp.bookingapp.entity.ResourceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateResourceRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @Size(max = 2000)
        String description,

        @NotNull
        ResourceType type,

        @NotNull
        @DecimalMin(value = "0.00")
        @Digits(integer = 8, fraction = 2)
        BigDecimal unitPrice,

        @NotNull
        @Min(0)
        @Max(100_000)
        Integer totalCapacity
) {
}