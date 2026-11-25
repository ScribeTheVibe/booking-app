package com.bookingapp.bookingapp.service;

import com.bookingapp.bookingapp.dto.RegisterUserRequest;
import com.bookingapp.bookingapp.dto.UpdateUserRequest;
import com.bookingapp.bookingapp.entity.User;
import com.bookingapp.bookingapp.entity.UserStatus;
import com.bookingapp.bookingapp.exception.*;
import com.bookingapp.bookingapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       @Value("${app.admin.email:}") String adminEmail){
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = normalizeEmail(adminEmail);
    }

    private String normalizeEmail(String email){
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public Optional<User> findUserByEmail(String email){
        return userRepository.findByEmail(email);
    }

    public Optional<User> findUserByPhone(String phone){
        return userRepository.findByPhone(phone);
    }


    @Transactional
    public User createUser(RegisterUserRequest request){
        String email = normalizeEmail(request.email());

        if(userRepository.existsByEmail(email)){
            throw new EmailAlreadyInUseException(email);
        }
        if(userRepository.existsByPhone(request.phone())){
            throw new PhoneAlreadyInUseException(request.phone());
        }

        String hashedPassword = passwordEncoder.encode(request.password());

        User user = new User(
                request.firstName(),
                request.lastName(),
                hashedPassword,
                email,
                request.phone()
        );

        if (email.equals(adminEmail)) {
            user.promoteToAdmin();
            user.incrementTokenVersion();
        }

        return userRepository.save(user);
    }


    @Transactional(readOnly = true)
    public User getUserById(Long id){
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("No user with id: " + id));
    }


    @Transactional
    public User updateUser(Long id,  UpdateUserRequest request){
        String email = normalizeEmail(request.email());
        User user = getUserById(id);

        if(!user.getEmail().equals(email) && userRepository.existsByEmail(email)){
            throw new DuplicateFieldException("email", email);
        }

        if(!user.getPhone().equals(request.phone()) && userRepository.existsByPhone(request.phone())){
            throw new DuplicateFieldException("phone", request.phone());
        }

        user.changeName(request.firstName(), request.lastName());
        user.changeEmail(email);
        user.changePhone(request.phone());

        return userRepository.save(user);
    }

    @Transactional
    public void softDeleteUser(Long id){
        User user = getUserById(id);
        user.incrementTokenVersion();
        user.markDeleted(Instant.now());
    }


    @Transactional
    public User login(String email, String rawPassword){
        String normalizedEmail = normalizeEmail(email);
        User user = findUserByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidUserCredentialException("Invalid email or password"));

        if(!passwordEncoder.matches(rawPassword, user.getPasswordHash())){
            throw new InvalidUserCredentialException("Invalid email or password");
        }

        if (normalizedEmail.equals(adminEmail) && !user.isAdmin()) {
            user.promoteToAdmin();
            user.incrementTokenVersion();
        }

        return user;
    }

    @Transactional
    public void changePassword(Long id, String currentPassword, String newPassword){
        User user = getUserById(id);

        if(!passwordEncoder.matches(currentPassword, user.getPasswordHash())){
            throw new InvalidUserCredentialException("Current password is incorrect");
        }
        user.incrementTokenVersion();
        user.changePasswordHash(passwordEncoder.encode(newPassword));
    }


    @Transactional(readOnly = true)
    public Page<User> listUsers(UserStatus status, Pageable pageable){
        return switch (status){
            case ACTIVE -> userRepository.findAll(pageable);
            case DELETED -> userRepository.findAllDeleted(pageable);
            case ALL -> userRepository.findAllIncludingDeleted(pageable);
        };
    }

}
