package com.bookingapp.bookingapp.controller;

import com.bookingapp.bookingapp.dto.LoginRequest;
import com.bookingapp.bookingapp.dto.RegisterUserRequest;
import com.bookingapp.bookingapp.dto.UserResponse;
import com.bookingapp.bookingapp.entity.User;
import com.bookingapp.bookingapp.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterUserRequest request){
        return UserResponse.from(userService.createUser(request));
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request){
        User user = userService.login(request.email(), request.password());
        return UserResponse.from(user);
    }
}
