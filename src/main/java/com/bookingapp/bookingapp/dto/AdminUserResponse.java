package com.bookingapp.bookingapp.dto;

import com.bookingapp.bookingapp.entity.User;

import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        boolean admin,
        Instant createdAt,
        Instant deletedAt
) {
    public static AdminUserResponse from(User user){
        return new AdminUserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.isAdmin(),
                user.getCreatedAt(),
                user.getDeletedAt());
    }

}
