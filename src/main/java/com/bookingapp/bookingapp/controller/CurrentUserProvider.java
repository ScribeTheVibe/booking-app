package com.bookingapp.bookingapp.controller;

/**
 * The only way controllers learn who is calling. Implementations must read the id from the
 * authenticated principal, never from a request body, path or query parameter.
 */
public interface CurrentUserProvider {

    /** @throws org.springframework.web.server.ResponseStatusException 401 if nobody is authenticated */
    Long requireUserId();
}
