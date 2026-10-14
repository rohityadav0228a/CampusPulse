package com.campuspulse.controller;

import com.campuspulse.config.SecurityConfig;
import com.campuspulse.model.Student;
import com.campuspulse.service.UserManagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = AdminUserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "campuspulse.admin.username=testadmin",
        "campuspulse.admin.password=SecureAdminPass123!"
})
class AdminUserManagementSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserManagementService userManagementService;

    @Test
    @DisplayName("ROLE_ADMIN accesses user management page successfully (200 OK)")
    void adminCanAccessUserManagement() throws Exception {
        when(userManagementService.searchUsers(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(userManagementService.getRoleMetrics())
                .thenReturn(Map.of("TOTAL", 0L, "STUDENTS", 0L, "MODERATORS", 0L, "ADMINS", 0L));
        when(userManagementService.getRecentAuditHistory())
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/users")
                        .with(user("testadmin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users/index"));
    }

    @Test
    @DisplayName("ROLE_STUDENT access to user management is denied (403 Forbidden)")
    void studentCannotAccessUserManagement() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .with(user("student@campuspulse.edu").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_MODERATOR access to user management is denied (403 Forbidden)")
    void moderatorCannotAccessUserManagement() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .with(user("moderator@campuspulse.edu").roles("MODERATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request to user management is redirected to login (302)")
    void unauthenticatedUserRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("ROLE_ADMIN can execute role change with valid CSRF token")
    void adminCanChangeUserRole() throws Exception {
        Student student = new Student("Target User", "target@campuspulse.edu", "CS-101", "pass");
        student.setRole("ROLE_MODERATOR");
        when(userManagementService.updateUserRole(anyLong(), anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(student);

        mockMvc.perform(post("/admin/users/1/role")
                        .with(user("testadmin").roles("ADMIN"))
                        .with(csrf())
                        .param("role", "ROLE_MODERATOR")
                        .param("reason", "Promoting to moderator"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        verify(userManagementService).updateUserRole(1L, "ROLE_MODERATOR", "testadmin", "Promoting to moderator", false);
    }

    @Test
    @DisplayName("ROLE_STUDENT role change attempt is denied (403 Forbidden)")
    void studentCannotChangeRole() throws Exception {
        mockMvc.perform(post("/admin/users/1/role")
                        .with(user("student@campuspulse.edu").roles("STUDENT"))
                        .with(csrf())
                        .param("role", "ROLE_ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_MODERATOR role change attempt is denied (403 Forbidden)")
    void moderatorCannotChangeRole() throws Exception {
        mockMvc.perform(post("/admin/users/1/role")
                        .with(user("moderator@campuspulse.edu").roles("MODERATOR"))
                        .with(csrf())
                        .param("role", "ROLE_ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Role change request without CSRF token is rejected (403 Forbidden)")
    void roleChangeWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/admin/users/1/role")
                        .with(user("testadmin").roles("ADMIN"))
                        .param("role", "ROLE_MODERATOR"))
                .andExpect(status().isForbidden());
    }
}
