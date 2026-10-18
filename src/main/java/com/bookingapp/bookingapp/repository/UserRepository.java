package com.bookingapp.bookingapp.repository;

import com.bookingapp.bookingapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Emails are stored lower-cased, so callers must lower-case (and trim) before searching. */
    Optional<User> findByEmail(String email);

    /** Ignores letter case, but callers still need to trim surrounding whitespace. */
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);
}