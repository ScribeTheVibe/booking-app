package com.bookingapp.bookingapp.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * DEV ONLY placeholder until real authentication exists: trusts an X-Dev-User-Id header.
 * Active only under the "dev" profile. Never enable this profile in a deployment.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevHeaderCurrentUserProvider implements CurrentUserProvider {

    private static final String HEADER = "X-Dev-User-Id";

    private final HttpServletRequest request;

    @Override
    public Long requireUserId() {
        String value = request.getHeader(HEADER);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        try {
            long userId = Long.parseLong(value.trim());
            if (userId <= 0) {
                throw new NumberFormatException("User id must be positive");
            }
            return userId;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
    }
}
