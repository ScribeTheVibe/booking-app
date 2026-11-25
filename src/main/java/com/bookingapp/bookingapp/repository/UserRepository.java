package com.bookingapp.bookingapp.repository;

import com.bookingapp.bookingapp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Emails are stored lower-cased, so callers must lower-case (and trim) before searching. */
    Optional<User> findByEmail(String email);

    /** Ignores letter case, but callers still need to trim surrounding whitespace. */
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByPhone(String phone);

    @Query(value = "SELECT * FROM users WHERE id = :id", nativeQuery = true)
    Optional<User> findByIdIncludingDeleted(@Param("id") Long id);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);


    @Query(value = "SELECT * FROM users WHERE deleted_at IS NOT NULL",
    countQuery = "SELECT count(*) FROM users WHERE deleted_at IS NOT NULL",
    nativeQuery = true)
    Page<User> findAllDeleted(Pageable pageable);

    @Query(value = "SELECT * FROM users",
    countQuery = "SELECT count(*) FROM users",
    nativeQuery = true)
    Page<User> findAllIncludingDeleted(Pageable pageable);
}
