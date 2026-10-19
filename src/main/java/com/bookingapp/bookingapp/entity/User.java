package com.bookingapp.bookingapp.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;

/**
 * A customer account. Bookings are loaded through BookingRepository, not through this entity.
 * Email is stored trimmed and lower-cased so the unique constraint is not case-sensitive in practice.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "phone", nullable = false, unique = true, length = 30)
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    public User(String firstName, String lastName, String passwordHash, String email, String phone) {
        this.firstName = requireText(firstName, "First name");
        this.lastName = requireText(lastName, "Last name");
        this.passwordHash = requireText(passwordHash, "Password hash");
        this.email = normalizeEmail(email);
        this.phone = requireText(phone, "Phone");
    }

    public void changeName(String firstName, String lastName) {
        this.firstName = requireText(firstName, "First name");
        this.lastName = requireText(lastName, "Last name");
    }

    public void changeEmail(String email) {
        this.email = normalizeEmail(email);
    }

    public void changePhone(String phone) {
        this.phone = requireText(phone, "Phone");
    }

    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = requireText(newPasswordHash, "Password hash");
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    private static String normalizeEmail(String email) {
        return requireText(email, "Email").toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User that)) return false;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return User.class.hashCode();
    }
}
