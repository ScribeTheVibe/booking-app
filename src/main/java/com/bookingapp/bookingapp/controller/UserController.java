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

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id){
        return UserResponse.from(userService.getUserById(id));
    }

    @PostMapping("/{id}")
    public UserResponse update(@PathVariable Long id,
                               @Valid @RequestBody UpdateUserRequest request){
        User updated = userService.updateUser(id, currentUser.requireUserId(), request);
        return UserResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id){
        userService.softDeleteUser(id, currentUser.requireUserId());
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@PathVariable Long id,
                               @Valid @RequestBody ChangePasswordRequest request){
        userService.changePassword(id, currentUser.requireUserId(), request.currentPassword(),
                request.newPassword());
    }

}
