package com.bookingapp.bookingapp.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The three fixed resort sessions. The times are placeholders: replace them with the resort's real hours.
 * All times are in the resort's time zone (see ResortTime).
 */
public enum Session {

    MORNING(LocalTime.of(9, 0), LocalTime.of(13, 0), EnumSet.of(HalfDay.AM)),
    EVENING(LocalTime.of(13, 0), LocalTime.of(17, 0), EnumSet.of(HalfDay.PM)),
    FULL_DAY(LocalTime.of(9, 0), LocalTime.of(17, 0), EnumSet.of(HalfDay.AM, HalfDay.PM));

    private final LocalTime start;
    private final LocalTime end;
    private final Set<HalfDay> halfDays;

    Session(LocalTime start, LocalTime end, Set<HalfDay> halfDays) {
        this.start = start;
        this.end = end;
        this.halfDays = Collections.unmodifiableSet(halfDays);
    }

    public ZonedDateTime startAt(LocalDate date) {
        return ZonedDateTime.of(date, start, ResortTime.ZONE);
    }

    public ZonedDateTime endAt(LocalDate date) {
        return ZonedDateTime.of(date, end, ResortTime.ZONE);
    }

    public boolean covers(HalfDay halfDay) {
        return halfDays.contains(halfDay);
    }

    public Set<HalfDay> halfDays() {
        return halfDays;
    }
}