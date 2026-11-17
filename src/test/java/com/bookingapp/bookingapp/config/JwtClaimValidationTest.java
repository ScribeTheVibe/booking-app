package com.bookingapp.bookingapp.config;

import com.bookingapp.bookingapp.entity.User;
import com.bookingapp.bookingapp.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest
class JwtClaimValidationTest {

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void validTokenIsAccepted() {
        // Create a valid token using JwtService
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.isAdmin()).thenReturn(false);

        String token = jwtService.generateToken(user);
        assertThatCode(() -> jwtDecoder.decode(token))
                .doesNotThrowAnyException();
    }

    @Test
    void tokenWithoutExpirationIsRejected() {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("booking-app")
                .audience(List.of("booking-app-api"))
                .subject("1")
                .issuedAt(Instant.now())
                // No expiresAt
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        assertThatThrownBy(() -> jwtDecoder.decode(token))
                .hasMessageContaining("Token must contain an expiration claim");
    }

    @Test
    void tokenWithWrongIssuerIsRejected() {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("evil-app")
                .audience(List.of("booking-app-api"))
                .subject("1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        assertThatThrownBy(() -> jwtDecoder.decode(token))
                .hasMessageContaining("Invalid token issuer");
    }

    @Test
    void tokenWithWrongAudienceIsRejected() {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("booking-app")
                .audience(List.of("wrong-api"))
                .subject("1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        assertThatThrownBy(() -> jwtDecoder.decode(token))
                .hasMessageContaining("Invalid token audience");
    }

    @Test
    void tokenWithMissingIssuerIsRejected() {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .audience(List.of("booking-app-api"))
                .subject("1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        assertThatThrownBy(() -> jwtDecoder.decode(token))
                .hasMessageContaining("Invalid token issuer");
    }

    @Test
    void tokenWithMissingAudienceIsRejected() {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("booking-app")
                .subject("1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        assertThatThrownBy(() -> jwtDecoder.decode(token))
                .hasMessageContaining("Invalid token audience");
    }
}
