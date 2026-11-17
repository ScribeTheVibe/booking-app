package com.bookingapp.bookingapp.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

@Configuration
public class JwtConfig {

    @Bean
    SecretKey jwtSecretKey(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if(bytes.length < 32){
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes");
        }
    return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey){
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey,
                           @Value("${app.jwt.issuer:booking-app}") String expectedIssuer,
                           @Value("${app.jwt.audience:booking-app-api}") String expectedAudience){
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> claimValidator = jwt -> {
            // Require expiration claim
            if (jwt.getExpiresAt() == null) {
                OAuth2Error noExpError = new OAuth2Error("missing_exp",
                        "Token must contain an expiration claim", null);
                return OAuth2TokenValidatorResult.failure(noExpError);
            }
            // Validate issuer
            Object issuerObj = jwt.getClaims().get("iss");
            String issuer = issuerObj != null ? issuerObj.toString() : null;
            if (issuer == null || !issuer.equals(expectedIssuer)) {
                OAuth2Error issuerError = new OAuth2Error("invalid_issuer",
                        "Invalid token issuer", null);
                return OAuth2TokenValidatorResult.failure(issuerError);
            }
            // Validate audience
            Collection<String> audience = jwt.getAudience();
            if (audience == null || audience.isEmpty() || !audience.contains(expectedAudience)) {
                OAuth2Error audError = new OAuth2Error("invalid_audience",
                        "Invalid token audience", null);
                return OAuth2TokenValidatorResult.failure(audError);
            }
            return OAuth2TokenValidatorResult.success();
        };
        decoder.setJwtValidator(claimValidator);
        return decoder;
    }

}
