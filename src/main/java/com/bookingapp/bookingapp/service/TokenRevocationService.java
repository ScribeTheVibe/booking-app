package com.bookingapp.bookingapp.service;

import com.bookingapp.bookingapp.entity.User;
import com.bookingapp.bookingapp.repository.UserRepository;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Validates that a JWT's tokenVersion claim matches the user's current token version in the database.
 * When a user changes their password, deletes their account, or has their admin status changed,
 * the tokenVersion is incremented, causing all previously-issued tokens to become invalid.
 */
@Service
public class TokenRevocationService {

    private final UserRepository userRepository;

    public TokenRevocationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Validates that the user exists and their current tokenVersion matches the version in the JWT.
     * If the versions don't match, the token is considered revoked and an exception is thrown.
     *
     * @param userId the user ID from the JWT subject
     * @param tokenVersion the tokenVersion claim from the JWT
     */
    @Transactional(readOnly = true)
    public void validateTokenVersion(Long userId, int tokenVersion) {
        // We need to bypass the @SQLRestriction to check deleted users as well,
        // so we use findById which still respects the restriction. Instead, we query directly.
        Optional<User> userOpt = userRepository.findById(userId);
        
        if (userOpt.isEmpty()) {
            // User not found or deleted - token is revoked
            throw new InvalidBearerTokenException(
                "Token revoked: user account no longer active");
        }

        User user = userOpt.get();
        if (user.getTokenVersion() != tokenVersion) {
            throw new InvalidBearerTokenException(
                "Token revoked: token version mismatch");
        }
    }
}
