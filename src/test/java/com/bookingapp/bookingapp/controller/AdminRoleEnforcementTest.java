package com.bookingapp.bookingapp.controller;

import com.bookingapp.bookingapp.config.JwtAuthConverter;
import com.bookingapp.bookingapp.config.JwtConfig;
import com.bookingapp.bookingapp.config.SecurityConfig;
import com.bookingapp.bookingapp.service.ResourceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ResourceController.class)
@Import({SecurityConfig.class, JwtConfig.class, SecurityCurrentUserProvider.class})
class AdminRoleEnforcementTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ResourceService resourceService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private JwtAuthConverter jwtAuthConverter;

    private static UsernamePasswordAuthenticationToken customerAuth(Long userId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new BookingPrincipal(userId), null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private static UsernamePasswordAuthenticationToken adminAuth(Long userId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new BookingPrincipal(userId), null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void customerCannotListResources() throws Exception {
        mvc.perform(get("/api/admin/resources").with(authentication(customerAuth(1L))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(resourceService);
    }

    @Test
    void customerCannotCreateResource() throws Exception {
        mvc.perform(post("/api/admin/resources")
                        .with(authentication(customerAuth(1L)))
                        .contentType("application/json")
                        .content("""
                                {"name":"Test","description":"Desc","type":"TICKET","unitPrice":10.0,"totalCapacity":5}
                                """))
                .andExpect(status().isForbidden());
        verifyNoInteractions(resourceService);
    }

    @Test
    void customerCannotUpdateResource() throws Exception {
        mvc.perform(put("/api/admin/resources/1")
                        .with(authentication(customerAuth(1L)))
                        .contentType("application/json")
                        .content("""
                                {"version":0,"name":"Test","description":"Desc","type":"TICKET","unitPrice":10.0,"totalCapacity":5}
                                """))
                .andExpect(status().isForbidden());
        verifyNoInteractions(resourceService);
    }

    @Test
    void customerCannotActivateResource() throws Exception {
        mvc.perform(post("/api/admin/resources/1/activate").with(authentication(customerAuth(1L))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(resourceService);
    }

    @Test
    void customerCannotDeactivateResource() throws Exception {
        mvc.perform(post("/api/admin/resources/1/deactivate").with(authentication(customerAuth(1L))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(resourceService);
    }

    @Test
    void adminCanListResources() throws Exception {
        when(resourceService.findResources(any())).thenReturn(List.of());
        mvc.perform(get("/api/admin/resources").with(authentication(adminAuth(1L))))
                .andExpect(status().isOk());
        verify(resourceService).findResources(any());
    }

    @Test
    void adminCanGetResource() throws Exception {
        var resource = mock(com.bookingapp.bookingapp.entity.BookableResource.class);
        when(resource.getId()).thenReturn(1L);
        when(resource.getName()).thenReturn("Test");
        when(resource.getDescription()).thenReturn("Desc");
        when(resource.getType()).thenReturn(com.bookingapp.bookingapp.entity.ResourceType.TICKET);
        when(resource.getUnitPrice()).thenReturn(java.math.BigDecimal.TEN);
        when(resource.getTotalCapacity()).thenReturn(5);
        when(resource.getVersion()).thenReturn(0L);
        when(resource.isActive()).thenReturn(true);
        when(resourceService.getResourceById(1L)).thenReturn(resource);

        mvc.perform(get("/api/admin/resources/1").with(authentication(adminAuth(1L))))
                .andExpect(status().isOk());
        verify(resourceService).getResourceById(1L);
    }

    @Test
    void anonymousCannotAccessAdminEndpoints() throws Exception {
        mvc.perform(get("/api/admin/resources"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(resourceService);
    }
}
