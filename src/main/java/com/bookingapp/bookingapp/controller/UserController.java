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

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id){
        return UserResponse.from(userService.getUserById(id));
    }

    @PostMapping("/{id}")
    public UserResponse update(@PathVariable Long id,
                               @Valid @RequestBody UpdateUserRequest request, Long requestingUserId){
        User updated = userService.updateUser(id, requestingUserId, request);
        return UserResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id,
                               Long requestingUserId){
        userService.softDeleteUser(id, requestingUserId);
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@PathVariable Long id,
                               @Valid @RequestBody ChangePasswordRequest request,
                               Long requestingUserId){
        userService.changePassword(id, requestingUserId, request.currentPassword(), request.newPassword());
    }

}
