package com.bookingapp.bookingapp.controller;

import com.bookingapp.bookingapp.dto.SettingResponse;
import com.bookingapp.bookingapp.dto.UpdateRescheduleDeadlineRequest;
import com.bookingapp.bookingapp.service.SettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService settingService;

    @GetMapping
    public List<SettingResponse> list() {
        return settingService.getSettings().stream()
                .map(SettingResponse::from)
                .toList();
    }

    @GetMapping("/{key}")
    public SettingResponse get(@PathVariable String key) {
        return SettingResponse.from(settingService.getSettingById(key));
    }

    @PutMapping("/reschedule-deadline-hours")
    public SettingResponse changeRescheduleDeadlineHours(
            @Valid @RequestBody UpdateRescheduleDeadlineRequest request) {
        return SettingResponse.from(settingService.changeRescheduleDeadlineHours(request.hours()));
    }
}