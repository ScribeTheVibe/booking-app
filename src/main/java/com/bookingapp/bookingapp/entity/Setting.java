package com.bookingapp.bookingapp.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Admin-configurable values, such as the reschedule deadline in hours before session start.
 * Fields are named settingKey / settingValue because "key" and "value" are JPQL function names
 * and reserved words in some databases.
 */
@Entity
@Table(name = "settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Setting {

    public static final String RESCHEDULE_DEADLINE_HOURS = "reschedule_deadline_hours";

    @Id
    @Column(name = "setting_key", length = 100)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 500)
    private String settingValue;

    public Setting(String settingKey, String settingValue) {
        if (settingKey == null || settingKey.isBlank()) throw new IllegalArgumentException("Key is required");
        if (settingValue == null) throw new IllegalArgumentException("Value is required");
        this.settingKey = settingKey;
        this.settingValue = settingValue;
    }

    public void changeValue(String newValue) {
        if (newValue == null) throw new IllegalArgumentException("Value is required");
        this.settingValue = newValue;
    }

    public int asInt() {
        try {
            return Integer.parseInt(settingValue.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Setting '" + settingKey + "' is not a whole number: " + settingValue, e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Setting that)) return false;
        return getSettingKey() != null && getSettingKey().equals(that.getSettingKey());
    }

    @Override
    public int hashCode() {
        return Setting.class.hashCode();
    }
}