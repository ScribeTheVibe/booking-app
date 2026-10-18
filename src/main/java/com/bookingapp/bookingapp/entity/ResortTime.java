package com.bookingapp.bookingapp.entity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

/**
 * Resort-wide constants. The resort is in Lithuania, so every session time and every
 * "has it started" comparison uses Europe/Vilnius, regardless of server or customer time zone.
 */
public final class ResortTime {

    public static final ZoneId ZONE = ZoneId.of("Europe/Vilnius");

    private static final Set<DayOfWeek> OPEN_DAYS =
            EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    private ResortTime() {
    }

    public static boolean isOpenDay(LocalDate date) {
        return OPEN_DAYS.contains(date.getDayOfWeek());
    }
}