package com.bookingapp.bookingapp.controller;

import com.bookingapp.bookingapp.dto.ChangePasswordRequest;
import com.bookingapp.bookingapp.dto.UpdateUserRequest;
import com.bookingapp.bookingapp.dto.UserResponse;
import com.bookingapp.bookingapp.entity.User;
import com.bookingapp.bookingapp.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CurrentUserProvider currentUser;

    @GetMapping("/me")
    public UserResponse me(){
        return UserResponse.from(userService.getUserById(currentUser.requireUserId()));
    }

    @PutMapping("/me")
    public UserResponse update(@Valid @RequestBody UpdateUserRequest request){
        User updated = userService.updateUser(currentUser.requireUserId(), request);
        return UserResponse.from(updated);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(){
        userService.softDeleteUser(currentUser.requireUserId());
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request){
        userService.changePassword(currentUser.requireUserId(), request.currentPassword(),
                request.newPassword());
    }

}
