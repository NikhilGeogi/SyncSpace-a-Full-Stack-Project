package com.example.Final.controller;

import com.example.Final.entity.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class WebController {

    @GetMapping({"/", "/index"})
    public String index(HttpSession session, Model model) {
        Object userId = session.getAttribute(UserController.AUTHENTICATED_USER_ID);
        Object username = session.getAttribute(UserController.AUTHENTICATED_USERNAME);
        if (userId == null || username == null) return "redirect:/login";
        model.addAttribute("currentUserId", userId);
        model.addAttribute("currentUsername", username);
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login"; // 對應 src/main/resources/templates/login.html
    }

    @GetMapping("/register")
    public String register() {
        return "register"; // 對應 src/main/resources/templates/register.html
    }

    @GetMapping("/public/{token}")
    public String publicPage(@PathVariable String token, Model model) {
        model.addAttribute("publicToken", token);
        return "public-page";
    }
}
