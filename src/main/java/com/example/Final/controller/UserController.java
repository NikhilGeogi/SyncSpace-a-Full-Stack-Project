/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.example.Final.controller;

import com.example.Final.entity.User;
import com.example.Final.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 *
 * @author aicpq
 */


@RestController
@RequestMapping("/api/users")
public class UserController {

    static final String AUTHENTICATED_USER_ID = "authenticatedUserId";
    static final String AUTHENTICATED_USERNAME = "authenticatedUsername";

    @Autowired
    private UserService userService;

    // POST http://localhost:8080/api/users/register
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User user) {
        if (user.getUsername() == null || user.getUsername().isBlank()
                || user.getEmail() == null || user.getEmail().isBlank()
                || user.getPassword() == null || user.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body("username, email, and password are required");
        }
        user.setId(null);
        String result = userService.register(user);
        if (result.equals("Registration successful")) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    // POST http://localhost:8080/api/users/login
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginData, HttpServletRequest request) {
        String email = loginData.get("email");
        String password = loginData.get("password");
        
        User user = userService.login(email, password);
        
        if (user != null) {
            HttpSession session = request.getSession(true);
            request.changeSessionId();
            session.setAttribute(AUTHENTICATED_USER_ID, user.getId());
            session.setAttribute(AUTHENTICATED_USERNAME, user.getUsername());
            return ResponseEntity.ok(Map.of("id", user.getId(), "username", user.getUsername()));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login failed");
    }

    @GetMapping("/session")
    public ResponseEntity<?> currentSession(HttpSession session) {
        Object userId = session.getAttribute(AUTHENTICATED_USER_ID);
        Object username = session.getAttribute(AUTHENTICATED_USERNAME);
        if (userId == null || username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
        }
        return ResponseEntity.ok(Map.of("id", userId, "username", username));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.noContent().build();
    }

    // PUT http://localhost:8080/api/users/{id}/username
    @PutMapping("/{id}/username")
    public ResponseEntity<String> updateUsername(@PathVariable Long id, @RequestBody Map<String, String> body,
                                                 HttpSession session) {
        if (!id.equals(session.getAttribute(AUTHENTICATED_USER_ID))) return ResponseEntity.status(403).body("Forbidden");
        String currentPassword = body.get("currentPassword");
        String newUsername = body.get("newUsername");
        String result = userService.updateUsername(id, currentPassword, newUsername);
        if (result.equals("Update successful")) {
            session.setAttribute(AUTHENTICATED_USERNAME, newUsername);
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    // PUT http://localhost:8080/api/users/{id}/email
    @PutMapping("/{id}/email")
    public ResponseEntity<String> updateEmail(@PathVariable Long id, @RequestBody Map<String, String> body,
                                               HttpSession session) {
        if (!id.equals(session.getAttribute(AUTHENTICATED_USER_ID))) return ResponseEntity.status(403).body("Forbidden");
        String currentPassword = body.get("currentPassword");
        String newEmail = body.get("newEmail");
        String result = userService.updateEmail(id, currentPassword, newEmail);
        if (result.equals("Update successful")) return ResponseEntity.ok(result);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    // PUT http://localhost:8080/api/users/{id}/password
    @PutMapping("/{id}/password")
    public ResponseEntity<String> updatePassword(@PathVariable Long id, @RequestBody Map<String, String> body,
                                                  HttpSession session) {
        if (!id.equals(session.getAttribute(AUTHENTICATED_USER_ID))) return ResponseEntity.status(403).body("Forbidden");
        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");
        String result = userService.updatePassword(id, currentPassword, newPassword);
        if (result.equals("Update successful")) return ResponseEntity.ok(result);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }
}
