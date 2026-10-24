package com.campuspulse.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.savedrequest.RequestCache;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RoleBasedAuthenticationSuccessHandlerTest {

    private RequestCache requestCache;
    private RoleBasedAuthenticationSuccessHandler handler;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        requestCache = mock(RequestCache.class);
        handler = new RoleBasedAuthenticationSuccessHandler(requestCache);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
    }

    @Test
    @DisplayName("Admin authentication redirects to /admin/complaints and purges saved request cache")
    void onAuthenticationSuccess_adminUser() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin", "password", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        handler.onAuthenticationSuccess(request, response, auth);

        verify(requestCache).removeRequest(request, response);
        verify(response).sendRedirect("/admin/complaints");
    }

    @Test
    @DisplayName("Moderator authentication redirects to /moderator/complaints and purges saved request cache")
    void onAuthenticationSuccess_moderatorUser() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "moderator@campus.edu", "password", List.of(new SimpleGrantedAuthority("ROLE_MODERATOR")));

        handler.onAuthenticationSuccess(request, response, auth);

        verify(requestCache).removeRequest(request, response);
        verify(response).sendRedirect("/moderator/complaints");
    }

    @Test
    @DisplayName("Student authentication redirects to /complaints/new and purges saved request cache")
    void onAuthenticationSuccess_studentUser() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "student@campus.edu", "password", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));

        handler.onAuthenticationSuccess(request, response, auth);

        verify(requestCache).removeRequest(request, response);
        verify(response).sendRedirect("/complaints/new");
    }

    @Test
    @DisplayName("Unknown role redirects to /login?error=unauthorized and purges saved request cache")
    void onAuthenticationSuccess_unknownRole() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "unknown@campus.edu", "password", List.of(new SimpleGrantedAuthority("ROLE_GUEST")));

        handler.onAuthenticationSuccess(request, response, auth);

        verify(requestCache).removeRequest(request, response);
        verify(response).sendRedirect("/login?error=unauthorized");
    }
}
