package com.example.Final.service;

import com.example.Final.entity.User;
import com.example.Final.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 *
 * @author aicpq
 */
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public String register(User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            return "Username already exists";
        }
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            return "This email is already registered";
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
        return "Registration successful";
    }

    public User login(String email, String password) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !passwordMatches(password, user.getPassword())) return null;
        if (!isBcryptHash(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(password));
            userRepository.save(user);
        }
        return user;
    }

    public String updateUsername(Long userId, String currentPassword, String newUsername) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return "User not found";
        if (!passwordMatches(currentPassword, user.getPassword())) return "Wrong password";
        if (userRepository.findByUsername(newUsername).isPresent()) return "Username already exists";
        int result = userRepository.updateUsername(userId, newUsername);
        return result > 0 ? "Update successful" : "Update failed";
    }

    public String updateEmail(Long userId, String currentPassword, String newEmail) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return "User not found";
        if (!passwordMatches(currentPassword, user.getPassword())) return "Wrong password";
        if (userRepository.findByEmail(newEmail).isPresent()) return "Email already in use";
        int result = userRepository.updateEmail(userId, newEmail);
        return result > 0 ? "Update successful" : "Update failed";
    }

    public String updatePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return "User not found";
        if (!passwordMatches(currentPassword, user.getPassword())) return "Wrong password";
        int result = userRepository.updatePassword(userId, passwordEncoder.encode(newPassword));
        return result > 0 ? "Update successful" : "Update failed";
    }

    private boolean passwordMatches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) return false;
        return isBcryptHash(storedPassword)
                ? passwordEncoder.matches(rawPassword, storedPassword)
                : storedPassword.equals(rawPassword);
    }

    private boolean isBcryptHash(String password) {
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }
}
