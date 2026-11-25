package com.bookingapp.bookingapp.controller;

import com.bookingapp.bookingapp.dto.AdminUserResponse;
import com.bookingapp.bookingapp.dto.UserResponse;
import com.bookingapp.bookingapp.entity.UserStatus;
import com.bookingapp.bookingapp.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private static final int MAX_PAGE_SIZE = 100;

    @GetMapping("/{id}")
    public AdminUserResponse getUser(@PathVariable Long id){
        return AdminUserResponse.from(userService.getUserByIdIncludingDeleted(id));
    }

    @GetMapping
    public Page<AdminUserResponse> list(
            @RequestParam(defaultValue = "ACTIVE") UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size){

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("id"));

        return userService.listUsers(status, pageable).map(AdminUserResponse::from);
    }

    /** Promote a user to admin. */
    @PostMapping("/{userId}/promote")
    public AdminUserResponse promoteToAdmin(@PathVariable Long userId) {
        return AdminUserResponse.from(userService.promoteToAdmin(userId));
    }
}
