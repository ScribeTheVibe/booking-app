package com.bookingapp.bookingapp.config;

import com.bookingapp.bookingapp.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Seeds missing settings on startup. Idempotent: never overwrites an admin's value. */
@Component
@RequiredArgsConstructor
public class SettingsInitializer implements ApplicationRunner {

    private final SettingService settingService;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        settingService.ensureDefaults();
    }
}