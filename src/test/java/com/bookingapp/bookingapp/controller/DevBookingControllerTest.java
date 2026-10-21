package com.bookingapp.bookingapp.controller;

import com.bookingapp.bookingapp.service.BookingService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
@org.springframework.context.annotation.Import({DevHeaderCurrentUserProvider.class,
        com.bookingapp.bookingapp.config.SecurityConfig.class})
@ActiveProfiles("dev")
class DevBookingControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BookingService service;

    @MockitoBean
    private BookingMapper mapper;

    @Test
    void headerAuthenticatesList() throws Exception {
        when(service.getMyBookings(7L)).thenReturn(List.of());
        when(mapper.toSummaries(List.of())).thenReturn(List.of());
        mvc.perform(get("/api/bookings").header("X-Dev-User-Id", "7"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).getMyBookings(7L);
    }

    @Test
    void rejectsMissingAndInvalidIdentity() throws Exception {
        for (String value : List.of("", "abc", "0", "-1", "9223372036854775808")) {
            mvc.perform(get("/api/bookings").header("X-Dev-User-Id", value))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void validatesPostWithoutRequiringDevCsrfToken() throws Exception {
        mvc.perform(post("/api/bookings").header("X-Dev-User-Id", "7")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.numberOfPeople").exists());
        verifyNoInteractions(service);
    }

    @Test
    void scopesDetailToCallerAndHidesOtherUsersBookings() throws Exception {
        when(service.getMyBooking(12L, 7L)).thenThrow(new EntityNotFoundException("Booking not found"));
        mvc.perform(get("/api/bookings/12").header("X-Dev-User-Id", "7"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.title").value("Not found"));
        verify(service).getMyBooking(12L, 7L);
    }

    @Test
    void createReturnsLocationAndUsesCaller() throws Exception {
        var booking = mock(com.bookingapp.bookingapp.entity.Booking.class);
        when(booking.getId()).thenReturn(12L);
        when(service.createBooking(eq(7L), any(), any(), eq(1), any())).thenReturn(booking);
        mvc.perform(post("/api/bookings").header("X-Dev-User-Id", "7")
                        .contentType("application/json").content("""
                                {"date":"2030-01-04","session":"MORNING","numberOfPeople":1,
                                 "items":[{"resourceId":1,"quantity":1}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/bookings/12"));
        verify(service).createBooking(eq(7L), any(), any(), eq(1), any());
    }

    @Test
    void rescheduleConflictIsProblemResponse() throws Exception {
        when(service.rescheduleMyBooking(eq(12L), eq(7L), any()))
                .thenThrow(new com.bookingapp.bookingapp.exception.InvalidBookingStateException("Too late"));
        mvc.perform(post("/api/bookings/12/reschedule").header("X-Dev-User-Id", "7")
                        .contentType("application/json").content("{\"newDate\":\"2030-01-04\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Too late"));
    }
}