package com.bookingapp.bookingapp.controller;

import com.bookingapp.bookingapp.config.SecurityConfig;
import com.bookingapp.bookingapp.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
@Import({SecurityConfig.class, SecurityCurrentUserProvider.class})
class BookingSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BookingService service;

    @MockitoBean
    private BookingMapper mapper;

    @Test
    void devHeaderDoesNotAuthenticateOutsideDev() throws Exception {
        mvc.perform(get("/api/bookings").header("X-Dev-User-Id", "7"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void trustedPrincipalSuppliesCustomerId() throws Exception {
        when(service.getMyBookings(7L)).thenReturn(List.of());
        when(mapper.toSummaries(List.of())).thenReturn(List.of());
        mvc.perform(get("/api/bookings").with(authentication(
                        UsernamePasswordAuthenticationToken.authenticated(new BookingPrincipal(7L), null, List.of())))
                        .header("X-Dev-User-Id", "99"))
                .andExpect(status().isOk());
        verify(service).getMyBookings(7L);
    }

    @Test
    void genericUsernameIsNotACustomerIdentity() throws Exception {
        mvc.perform(get("/api/bookings").with(user("7")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void productionPostsRetainCsrfProtection() throws Exception {
        mvc.perform(post("/api/bookings").with(authentication(
                        UsernamePasswordAuthenticationToken.authenticated(new BookingPrincipal(7L), null, List.of())))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}