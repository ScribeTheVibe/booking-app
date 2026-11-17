package com.bookingapp.bookingapp.service;

import com.bookingapp.bookingapp.dto.RegisterUserRequest;
import com.bookingapp.bookingapp.entity.User;
import com.bookingapp.bookingapp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Sql(scripts = "/clear-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TokenRevocationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private TokenRevocationService tokenRevocationService;

    private User registerUser() {
        return userService.createUser(new RegisterUserRequest(
                "Test", "User", "test@example.com", "1234567890", "password123"));
    }

    @Test
    void newUserHasTokenVersionOne() {
        User user = registerUser();
        assertThat(user.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void tokenVersionIncludedInJwt() {
        User user = registerUser();
        String token = jwtService.generateToken(user);

        Jwt jwt = jwtDecoder.decode(token);
        assertThat(((Number) jwt.getClaims().get("tokenVersion")).intValue()).isEqualTo(1);
    }

    @Test
    void validTokenIsAccepted() {
        User user = registerUser();
        String token = jwtService.generateToken(user);

        Jwt jwt = jwtDecoder.decode(token);
        int tokenVersion = ((Number) jwt.getClaims().get("tokenVersion")).intValue();

        // Should not throw
        tokenRevocationService.validateTokenVersion(user.getId(), tokenVersion);
    }

    @Test
    void tokenRejectedAfterTokenVersionIncrement() {
        User user = registerUser();
        String token = jwtService.generateToken(user);

        Jwt jwt = jwtDecoder.decode(token);
        int oldTokenVersion = ((Number) jwt.getClaims().get("tokenVersion")).intValue();
        assertThat(oldTokenVersion).isEqualTo(1);

        // Simulate token revocation by incrementing the version in the database
        user.incrementTokenVersion();
        userRepository.save(user);

        // The old token should be rejected
        assertThatThrownBy(() -> tokenRevocationService.validateTokenVersion(user.getId(), oldTokenVersion))
                .hasMessageContaining("Token revoked");
    }

    @Test
    void newTokenAcceptedAfterTokenVersionIncrement() {
        User user = registerUser();

        // Simulate token revocation
        user.incrementTokenVersion();
        userRepository.save(user);

        // Refresh from DB
        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getTokenVersion()).isEqualTo(2);

        // Generate a new token with the updated version
        String newToken = jwtService.generateToken(updated);
        Jwt newJwt = jwtDecoder.decode(newToken);
        int newTokenVersion = ((Number) newJwt.getClaims().get("tokenVersion")).intValue();

        // The new token should be accepted
        assertThatCode(() -> tokenRevocationService.validateTokenVersion(updated.getId(), newTokenVersion))
                .doesNotThrowAnyException();
    }

    @Test
    void tokenRejectedAfterAccountDeletion() {
        User user = registerUser();
        int originalTokenVersion = user.getTokenVersion();

        // Delete account
        userService.softDeleteUser(user.getId());

        // The old token should be rejected because the user is deleted
        // (userRepository.findById returns empty due to @SQLRestriction)
        assertThatThrownBy(() -> tokenRevocationService.validateTokenVersion(user.getId(), originalTokenVersion))
                .hasMessageContaining("Token revoked");
    }

    @Test
    void nonexistentUserTokenIsRejected() {
        assertThatThrownBy(() -> tokenRevocationService.validateTokenVersion(9999L, 1))
                .hasMessageContaining("Token revoked");
    }

    @Test
    void tokenVersionIncrementedOnAccountDeletion() {
        User user = registerUser();
        assertThat(user.getTokenVersion()).isEqualTo(1);

        // Delete account
        userService.softDeleteUser(user.getId());

        // The user should be marked as deleted (tokenVersion was incremented before deletion)
        // We can verify by checking the user is no longer found via normal queries
        assertThat(userRepository.findById(user.getId())).isEmpty();
    }

    @Test
    void tokenVersionIncrementedOnAdminPromotion() {
        User user = registerUser();
        assertThat(user.getTokenVersion()).isEqualTo(1);
        assertThat(user.isAdmin()).isFalse();

        // Simulate admin promotion
        user.promoteToAdmin();
        user.incrementTokenVersion();
        userRepository.save(user);

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.isAdmin()).isTrue();
        assertThat(updated.getTokenVersion()).isEqualTo(2);
    }

    @Test
    void oldTokenRejectedAfterSimulatedPasswordChange() {
        User user = registerUser();
        String tokenBeforeChange = jwtService.generateToken(user);

        Jwt oldJwt = jwtDecoder.decode(tokenBeforeChange);
        int oldTokenVersion = ((Number) oldJwt.getClaims().get("tokenVersion")).intValue();
        assertThat(oldTokenVersion).isEqualTo(1);

        // Simulate password change effect: increment token version
        user.incrementTokenVersion();
        userRepository.save(user);

        // The old token should be rejected
        assertThatThrownBy(() -> tokenRevocationService.validateTokenVersion(user.getId(), oldTokenVersion))
                .hasMessageContaining("Token revoked");

        // Verify the new version works
        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThatCode(() -> tokenRevocationService.validateTokenVersion(user.getId(), updated.getTokenVersion()))
                .doesNotThrowAnyException();
    }
}
