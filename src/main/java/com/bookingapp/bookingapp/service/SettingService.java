package com.bookingapp.bookingapp.service;

import com.bookingapp.bookingapp.entity.Setting;
import com.bookingapp.bookingapp.repository.SettingRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin-configurable settings. The booking flow reads the reschedule deadline through
 * {@link #getRescheduleDeadlineHours()} instead of parsing raw settings itself.
 * <p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingService {

    /** Used only to seed the row on first start; the admin can change it afterwards. */
    public static final int DEFAULT_RESCHEDULE_DEADLINE_HOURS = 48;

    /** Sanity cap: one year. Catches typos such as an extra zero. */
    public static final int MAX_RESCHEDULE_DEADLINE_HOURS = 24 * 365;

    private final SettingRepository settingRepository;

    // ---------------------------------------------------------------- reads

    public Setting getSettingById(String key) {
        return settingRepository.findById(key).orElseThrow(() -> notFound(key));
    }

    public List<Setting> getSettings() {
        return settingRepository.findAll(Sort.by("settingKey"));
    }

    public int getRescheduleDeadlineHours() {
        int hours = settingRepository.findById(Setting.RESCHEDULE_DEADLINE_HOURS)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing setting: " + Setting.RESCHEDULE_DEADLINE_HOURS))
                .asInt();
        if (hours < 0) {
            throw new IllegalStateException(
                    Setting.RESCHEDULE_DEADLINE_HOURS + " must not be negative");
        }
        return hours;
    }

    // --------------------------------------------------------------- writes

    /** Creates the setting if it does not exist yet, otherwise updates it. */
    @Transactional
    public Setting changeRescheduleDeadlineHours(int hours) {
        if (hours < 0 || hours > MAX_RESCHEDULE_DEADLINE_HOURS) {
            throw new IllegalArgumentException(
                    "Reschedule deadline must be between 0 and " + MAX_RESCHEDULE_DEADLINE_HOURS + " hours");
        }
        String value = String.valueOf(hours);

        return settingRepository.findById(Setting.RESCHEDULE_DEADLINE_HOURS)
                .map(existing -> {
                    existing.changeValue(value); // managed entity: dirty checking saves it
                    return existing;
                })
                .orElseGet(() -> settingRepository.save(
                        new Setting(Setting.RESCHEDULE_DEADLINE_HOURS, value)));
    }

    /** Idempotent. Call once at startup so the booking flow never meets a missing setting. */
    @Transactional
    public void ensureDefaults() {
        if (!settingRepository.existsById(Setting.RESCHEDULE_DEADLINE_HOURS)) {
            settingRepository.save(new Setting(
                    Setting.RESCHEDULE_DEADLINE_HOURS,
                    String.valueOf(DEFAULT_RESCHEDULE_DEADLINE_HOURS)));
        }
    }

    // -------------------------------------------------------------- helpers

    private static EntityNotFoundException notFound(String key) {
        return new EntityNotFoundException("Setting " + key + " not found");
    }
}