package com.bookingapp.bookingapp.service;

import com.bookingapp.bookingapp.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration lifetime;
    private final String issuer;
    private final String audience;

    public JwtService(JwtEncoder encoder, Clock clock,
                      @Value("${app.jwt.expiration-minutes:30}") long minutes,
                      @Value("${app.jwt.issuer:booking-app}") String issuer,
                      @Value("${app.jwt.audience:booking-app-api}") String audience){
        this.encoder = encoder;
        this.clock =clock;
        this.lifetime = Duration.ofMinutes(minutes);
        this.issuer = issuer;
        this.audience = audience;
    }

    public String generateToken(User user){
        Instant now = clock.instant();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(now.plus(lifetime))
                .subject(user.getId().toString())
                .claim("tokenVersion", user.getTokenVersion());
        if (user.isAdmin()) {
            builder.claim("admin", true);
        }
        JwtClaimsSet claims = builder.build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long expiresInSeconds(){
        return lifetime.toSeconds();
    }
}
