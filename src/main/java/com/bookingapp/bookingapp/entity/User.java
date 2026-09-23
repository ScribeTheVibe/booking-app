package com.bookingapp.bookingapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Setter
    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Setter
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Setter
    @Column(name = "phone", nullable = false, unique = true)
    private String phone;

    protected User(){}

    public User(String firstName, String lastName, String passwordHash, String email, String phone){
        this.firstName = firstName;
        this.lastName = lastName;
        this.passwordHash = passwordHash;
        this.email = email;
        this.phone = phone;
    }
}
