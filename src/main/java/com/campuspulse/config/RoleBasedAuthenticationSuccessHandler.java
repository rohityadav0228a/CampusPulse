package com.campuspulse.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Directs authenticated users to their designated portal:
 * Admins (ROLE_ADMIN) are redirected to /admin/complaints.
 * Students (ROLE_STUDENT) are redirected to /complaints/new.
 */
@Component
public class RoleBasedAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(RoleBasedAuthenticationSuccessHandler.class);

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> "ROLE_ADMIN".equals(role) || "ADMIN".equals(role));

        if (isAdmin) {
            log.info("Admin user '{}' authenticated successfully. Redirecting to admin dashboard.", authentication.getName());
            response.sendRedirect("/admin/complaints");
        } else {
            log.info("Student user '{}' authenticated successfully. Redirecting to complaint submission.", authentication.getName());
            response.sendRedirect("/complaints/new");
        }
    }
}
