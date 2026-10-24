package com.campuspulse.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles failed login attempts by redirecting the user back to the login page they originated from:
 * Student attempts go to /student/login?error=true.
 * Admin attempts go to /login?error=true.
 *
 * Keeps failure responses safe and generic to prevent username enumeration or timing attacks,
 * while providing sanitized diagnostic logging that categorizes the failure reason without
 * revealing plaintext passwords, password hashes, or sensitive tokens.
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
        String username = request.getParameter("username");

        boolean isStudentLogin = "student".equalsIgnoreCase(loginType)
                || (referer != null && referer.contains("/student/login"));

        String category = categorizeException(exception);
        String maskedUser = sanitizeUsername(username);

        if (isStudentLogin) {
            log.warn("Student authentication failed [category={}, user={}]: {}", category, maskedUser, exception.getMessage());
            response.sendRedirect("/student/login?error=true");
        } else {
            log.warn("Admin authentication failed [category={}, user={}]: {}", category, maskedUser, exception.getMessage());
            response.sendRedirect("/login?error=true");
        }
    }

    private String categorizeException(AuthenticationException exception) {
        if (exception == null) {
            return "UNKNOWN";
        }
        if (exception instanceof BadCredentialsException) {
            return "BAD_CREDENTIALS";
        }
        if (exception instanceof UsernameNotFoundException) {
            return "USER_NOT_FOUND";
        }
        if (exception instanceof LockedException) {
            return "ACCOUNT_LOCKED";
        }
        if (exception instanceof DisabledException) {
            return "ACCOUNT_DISABLED";
        }
        if (exception instanceof AccountExpiredException) {
            return "ACCOUNT_EXPIRED";
        }
        if (exception instanceof CredentialsExpiredException) {
            return "CREDENTIALS_EXPIRED";
        }
        return exception.getClass().getSimpleName();
    }

    private String sanitizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return "[empty]";
        }
        String trimmed = username.trim();
        int atIndex = trimmed.indexOf('@');
        if (atIndex >= 0) {
            if (atIndex <= 1) {
                return "***" + trimmed.substring(atIndex);
            }
            return trimmed.charAt(0) + "***" + trimmed.substring(atIndex);
        }
        if (trimmed.length() <= 2) {
            return "***";
        }
        return trimmed.charAt(0) + "***" + trimmed.charAt(trimmed.length() - 1);
    }
}
