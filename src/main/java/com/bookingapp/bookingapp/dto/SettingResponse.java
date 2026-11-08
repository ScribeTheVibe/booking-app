package com.bookingapp.bookingapp.dto;

import com.bookingapp.bookingapp.entity.Setting;

public record SettingResponse(String key, String value) {

    public static SettingResponse from(Setting setting) {
        return new SettingResponse(setting.getSettingKey(), setting.getSettingValue());
    }
}