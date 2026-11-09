package com.bookingapp.bookingapp.config;

import com.bookingapp.bookingapp.controller.BookingPrincipal;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt){
        String subject = jwt.getSubject();
        if(subject == null){
                throw new InvalidBearerTokenException("Token has no subject");
        }
        try {
            Long userId = Long.valueOf(jwt.getSubject());
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
            return UsernamePasswordAuthenticationToken.authenticated(
                    new BookingPrincipal(userId), jwt, authorities);
        } catch (IllegalArgumentException e) {
            throw new InvalidBearerTokenException("Invalid subject in token", e);
        }
    }
}
