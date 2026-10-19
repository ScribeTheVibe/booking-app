package com.bookingapp.bookingapp.dto;

import com.bookingapp.bookingapp.entity.User;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone
) {
    public static UserResponse from(User user){
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone()
        );
    }
}
