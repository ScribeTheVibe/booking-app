package com.bookingapp.bookingapp.repository;

import com.bookingapp.bookingapp.entity.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

/** The id is the setting key, so findById(Setting.RESCHEDULE_DEADLINE_HOURS) is the lookup. */
public interface SettingRepository extends JpaRepository<Setting, String> {
}