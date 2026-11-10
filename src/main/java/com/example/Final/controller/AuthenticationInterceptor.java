package com.example.Final.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        var session = request.getSession(false);
        if (session != null && session.getAttribute(UserController.AUTHENTICATED_USER_ID) != null) {
            return true;
        }
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
        return false;
    }
}
