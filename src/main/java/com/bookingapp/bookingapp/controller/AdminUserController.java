package com.bookingapp.bookingapp.controller;

import com.bookingapp.bookingapp.dto.AdminUserResponse;
import com.bookingapp.bookingapp.entity.UserStatus;
import com.bookingapp.bookingapp.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private static final int MAX_PAGE_SIZE = 100;

    @GetMapping("/{id}")
    public AdminUserResponse getUser(@PathVariable Long id){
        return AdminUserResponse.from(userService.getUserById(id));
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
}
