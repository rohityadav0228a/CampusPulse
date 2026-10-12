package com.campuspulse.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles failed login attempts by redirecting the user back to the login page they originated from:
 * Student attempts go to /student/login?error=true.
 * Admin attempts go to /login?error=true.
 */
@Component
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomAuthenticationFailureHandler.class);

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        String referer = request.getHeader("Referer");
        String loginType = request.getParameter("loginType");

        boolean isStudentLogin = "student".equalsIgnoreCase(loginType)
                || (referer != null && referer.contains("/student/login"));

        if (isStudentLogin) {
            log.warn("Student authentication failed: {}", exception.getMessage());
            response.sendRedirect("/student/login?error=true");
        } else {
            log.warn("Admin authentication failed: {}", exception.getMessage());
            response.sendRedirect("/login?error=true");
        }
    }
}
