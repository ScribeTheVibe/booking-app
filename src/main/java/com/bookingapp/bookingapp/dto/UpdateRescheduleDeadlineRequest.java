package com.bookingapp.bookingapp.dto;

import com.bookingapp.bookingapp.service.SettingService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Cutoff for customer reschedules, in whole hours before the session starts.
 * Integer (not int) so a missing field is reported as "required" instead of becoming 0.
 */
public record UpdateRescheduleDeadlineRequest(

        @NotNull
        @Min(0)
        @Max(SettingService.MAX_RESCHEDULE_DEADLINE_HOURS)
        Integer hours
) {
}