package com.bookingapp.bookingapp.service;

import com.bookingapp.bookingapp.dto.RegisterUserRequest;
import com.bookingapp.bookingapp.dto.UpdateUserRequest;
import com.bookingapp.bookingapp.entity.User;
import com.bookingapp.bookingapp.exception.*;
import com.bookingapp.bookingapp.repository.BookingRepository;
import com.bookingapp.bookingapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BookingRepository bookingRepository;
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
        user.markDeleted(Instant.now());
    }


    public User login(String email, String rawPassword){
        String normalizedEmail = normalizeEmail(email);
        User user = findUserByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidUserCredentialException("Invalid email or password"));

        if(!passwordEncoder.matches(rawPassword, user.getPasswordHash())){
            throw new InvalidUserCredentialException("Invalid email or password");
        }
        return user;
    }

    @Transactional
    public void changePassword(Long id, String currentPassword, String newPassword){
        User user = getUserById(id);

        if(!passwordEncoder.matches(currentPassword, user.getPasswordHash())){
            throw new InvalidUserCredentialException("Current password is incorrect");
        }
        user.changePasswordHash(passwordEncoder.encode(newPassword));
    }

}
