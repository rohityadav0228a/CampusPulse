package com.campuspulse.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Directs authenticated users to their designated portal:
 * Admins (ROLE_ADMIN) are redirected to /admin/complaints.
 * Moderators (ROLE_MODERATOR) are redirected to /moderator/complaints.
 * Students (ROLE_STUDENT) are redirected to /complaints/new.
 *
 * Ensures any stale SavedRequest from previous unauthenticated requests or
 * other browser tabs is purged, preventing unexpected post-login redirection loops.
 */
@Component
public class RoleBasedAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(RoleBasedAuthenticationSuccessHandler.class);

    private final RequestCache requestCache;

    public RoleBasedAuthenticationSuccessHandler() {
        this(new HttpSessionRequestCache());
    }

    public RoleBasedAuthenticationSuccessHandler(RequestCache requestCache) {
        this.requestCache = requestCache != null ? requestCache : new HttpSessionRequestCache();
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        // Clear any saved request from cache to prevent stale redirects in multi-tab workflows
        if (requestCache != null) {
            requestCache.removeRequest(request, response);
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> "ROLE_ADMIN".equals(role) || "ADMIN".equals(role));

        boolean isModerator = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> "ROLE_MODERATOR".equals(role) || "MODERATOR".equals(role));

        boolean isStudent = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> "ROLE_STUDENT".equals(role) || "STUDENT".equals(role)
                        || (role != null && role.contains("STUDENT")));

        if (isAdmin) {
            log.info("Admin user '{}' authenticated successfully. Redirecting to admin dashboard.", authentication.getName());
            response.sendRedirect("/admin/complaints");
        } else if (isModerator) {
            log.info("Moderator user '{}' authenticated successfully. Redirecting to moderator dashboard.", authentication.getName());
            response.sendRedirect("/moderator/complaints");
        } else if (isStudent) {
            log.info("Student user '{}' authenticated successfully. Redirecting to complaint submission.", authentication.getName());
            response.sendRedirect("/complaints/new");
        } else {
            log.warn("User '{}' authenticated with unrecognized role {}. Redirecting to safe sign-in error.",
                    authentication.getName(), authentication.getAuthorities());
            response.sendRedirect("/login?error=unauthorized");
        }
    }
}
