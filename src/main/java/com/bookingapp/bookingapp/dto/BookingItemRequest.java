package com.bookingapp.bookingapp.dto;

/** Requested units of one resource; BookingService validates and merges duplicate lines. */
public record BookingItemRequest(Long resourceId, Integer quantity) {}