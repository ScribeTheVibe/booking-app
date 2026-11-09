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

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration lifetime;

    public JwtService(JwtEncoder encoder, Clock clock, @Value("${app.jwt.expiration-minutes:30}") long minutes){
        this.encoder = encoder;
        this.clock =clock;
        this.lifetime = Duration.ofMinutes(minutes);
    }

    public String generateToken(User user){
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("booking-app")
                .issuedAt(now)
                .expiresAt(now.plus(lifetime))
                .subject(user.getId().toString())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long expiresInSeconds(){
        return lifetime.toSeconds();
    }
}
